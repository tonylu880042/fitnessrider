import SwiftUI
import AVFoundation
import UniformTypeIdentifiers

/// Layer 2「app 內音樂庫」的一筆項目：曲名、時長、BPM 一律來自 `ClassRepository.fetchWaveform`
/// 的波形快取，不在這裡重新分析音檔（分析由 Layer 1 的匯入流程或快取命中負責）。
struct MusicLibraryTrack: Identifiable, Equatable {
    var id: String { fileName }
    let fileName: String
    let title: String
    let durationMs: Int
    let bpm: Double
}

/// 由 `Music/` 目錄下的檔名 + 波形快取查詢建立音樂庫列表，並依曲名排序。
/// `waveformLookup` 回傳 (durationMs, bpm)；查無快取時 fallback 回段落預設值，不觸發重新分析。
func buildMusicLibraryTracks(
    fileNames: [String],
    waveformLookup: (String) -> (Int, Double)?
) -> [MusicLibraryTrack] {
    fileNames.map { fileName -> MusicLibraryTrack in
        let cached = waveformLookup(fileName)
        let durationMs = (cached?.0 ?? 0) > 0 ? cached!.0 : 300_000
        let bpm = (cached?.1 ?? 0) > 0 ? cached!.1 : 128.0
        return MusicLibraryTrack(
            fileName: fileName,
            title: musicTitleFromFileName(fileName),
            durationMs: durationMs,
            bpm: bpm
        )
    }.sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
}

/// 即時搜尋 filter，比照舊版 FragDialogSelectMusic：不分大小寫比對曲名子字串。
func filterMusicLibraryTracks(_ tracks: [MusicLibraryTrack], query: String) -> [MusicLibraryTrack] {
    let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !trimmed.isEmpty else { return tracks }
    return tracks.filter { $0.title.range(of: trimmed, options: .caseInsensitive) != nil }
}

/// 從音樂庫多選既有曲目 -> 直接建立 N 個段落，行為對應 Layer 1 的
/// `buildSegmentsForImportedTracks`，唯一差異是曲目已經在 `Music/` 目錄裡，不會再產生檔案複製。
func buildSegmentsFromLibrarySelection(
    tracks: [MusicLibraryTrack],
    classId: UUID,
    startOrderIndex: Int
) -> [WorkoutSegment] {
    let importInfos = tracks.map { ImportedTrackInfo(fileName: $0.fileName, durationMs: $0.durationMs, bpm: $0.bpm) }
    return buildSegmentsForImportedTracks(tracks: importInfos, classId: classId, startOrderIndex: startOrderIndex)
}

private func formatDuration(_ durationMs: Int) -> String {
    let totalSec = durationMs / 1000
    return String(format: "%02d:%02d", totalSec / 60, totalSec % 60)
}

/// Layer 2：app 內音樂庫。段落指定音樂時開這個列表取代直接開系統檔案選擇器；
/// `.fileImporter` 只保留在畫面內「＋匯入新檔」這一個入口。選既有曲目時完全不複製檔案，
/// 直接沿用 `Music/` 目錄裡已經有的檔名（與其已經算好、存進波形快取的 duration/BPM）。
///
/// 試聽沿用 AVFoundation（app 既有的音訊框架，`WaveformAnalyzer`／`AudioEngineManager` 都建構在它上面），
/// 用內建的 `AVAudioPlayer` 播放單一檔案，沒有引入任何新的播放器套件或依賴。
struct MusicLibraryView: View {
    let classId: UUID
    let startOrderIndex: Int
    let onDismiss: () -> Void
    let onSegmentsCreated: ([WorkoutSegment]) -> Void
    let onImportFailed: ([String]) -> Void

    @State private var searchQuery: String = ""
    @State private var allTracks: [MusicLibraryTrack] = []
    @State private var selectedFileNames: Set<String> = []
    @State private var isImporting: Bool = false
    @State private var isShowingFileImporter: Bool = false
    @State private var previewPlayer: AVAudioPlayer?
    @State private var playingFileName: String?

    private var filteredTracks: [MusicLibraryTrack] {
        filterMusicLibraryTracks(allTracks, query: searchQuery)
    }

    var body: some View {
        VStack(spacing: 0) {
            TopNavBar(
                title: "",
                leading: {
                    Button("取消") {
                        stopPreview()
                        onDismiss()
                    }
                },
                trailing: {
                    Button {
                        isShowingFileImporter = true
                    } label: {
                        Label("匯入新檔", systemImage: "plus")
                            .font(.system(size: 15, weight: .bold))
                    }
                }
            )
            .overlay(
                Text("音樂庫")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
            )

            // 搜尋列：比照舊版 FragDialogSelectMusic 的即時 filter。
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(FitnessRiderTheme.textSecondary)
                TextField("搜尋曲名...", text: $searchQuery)
            }
            .padding(10)
            .background(FitnessRiderTheme.cardBackground)
            .cornerRadius(8)
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(FitnessRiderTheme.cardBorder, lineWidth: 1)
            )
            .padding(16)

            if filteredTracks.isEmpty {
                Spacer()
                VStack(spacing: 8) {
                    Image(systemName: "music.note")
                        .font(.system(size: 48))
                        .foregroundColor(FitnessRiderTheme.textMuted)
                    Text(allTracks.isEmpty ? "尚未匯入任何音樂，點右上角「匯入新檔」開始" : "找不到符合的曲目")
                        .font(.system(size: 14))
                        .foregroundColor(FitnessRiderTheme.textSecondary)
                }
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: 8) {
                        ForEach(filteredTracks) { track in
                            trackRow(track)
                        }
                    }
                    .padding(.horizontal, 16)
                }
            }

            Divider()

            Button {
                let selected = allTracks.filter { selectedFileNames.contains($0.fileName) }
                guard !selected.isEmpty else { return }
                let newSegments = buildSegmentsFromLibrarySelection(
                    tracks: selected,
                    classId: classId,
                    startOrderIndex: startOrderIndex
                )
                stopPreview()
                onSegmentsCreated(newSegments)
                onDismiss()
            } label: {
                Text(isImporting ? "匯入中..." : "加入 \(selectedFileNames.count) 首到課表")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(selectedFileNames.isEmpty || isImporting ? FitnessRiderTheme.textMuted : FitnessRiderTheme.topBarGreen)
                    .cornerRadius(10)
            }
            .disabled(selectedFileNames.isEmpty || isImporting)
            .padding(16)
        }
        .background(FitnessRiderTheme.canvasWhite)
        .onAppear { reload() }
        .onDisappear { stopPreview() }
        .fileImporter(
            isPresented: $isShowingFileImporter,
            allowedContentTypes: [UTType.audio, UTType.mp3, UTType.mpeg4Audio],
            allowsMultipleSelection: true
        ) { result in
            handleImportedMusic(result)
        }
    }

    // MARK: - Rows

    private func trackRow(_ track: MusicLibraryTrack) -> some View {
        let isSelected = selectedFileNames.contains(track.fileName)
        let isPlaying = playingFileName == track.fileName

        return HStack(spacing: 12) {
            Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                .font(.system(size: 20))
                .foregroundColor(isSelected ? FitnessRiderTheme.topBarGreen : FitnessRiderTheme.textMuted)

            VStack(alignment: .leading, spacing: 3) {
                Text(track.title)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(FitnessRiderTheme.textPrimary)
                    .lineLimit(1)
                Text("\(formatDuration(track.durationMs)) ・ \(Int(track.bpm)) BPM")
                    .font(.system(size: 12))
                    .foregroundColor(FitnessRiderTheme.textSecondary)
            }

            Spacer()

            Button {
                togglePreview(track)
            } label: {
                Image(systemName: isPlaying ? "pause.circle.fill" : "play.circle.fill")
                    .font(.system(size: 26))
                    .foregroundColor(FitnessRiderTheme.topBarGreenDark)
            }
            .buttonStyle(.plain)
        }
        .padding(12)
        .background(isSelected ? FitnessRiderTheme.topBarGreen.opacity(0.1) : FitnessRiderTheme.cardBackground)
        .cornerRadius(10)
        .contentShape(Rectangle())
        .onTapGesture {
            if isSelected {
                selectedFileNames.remove(track.fileName)
            } else {
                selectedFileNames.insert(track.fileName)
            }
        }
    }

    // MARK: - Data

    private func reload() {
        let musicDir = SQLiteDatabase.shared.musicDirectoryURL
        let fileNames = (try? FileManager.default.contentsOfDirectory(atPath: musicDir.path)) ?? []
        allTracks = buildMusicLibraryTracks(fileNames: fileNames) { fileName in
            guard let cached = ClassRepository.shared.fetchWaveform(for: fileName) else { return nil }
            return (cached.durationMs, cached.bpm)
        }
    }

    // MARK: - Preview Playback

    private func togglePreview(_ track: MusicLibraryTrack) {
        if playingFileName == track.fileName {
            stopPreview()
            return
        }
        stopPreview()
        let fileURL = SQLiteDatabase.shared.musicDirectoryURL.appendingPathComponent(track.fileName)
        guard FileManager.default.fileExists(atPath: fileURL.path) else { return }
        do {
            let player = try AVAudioPlayer(contentsOf: fileURL)
            player.prepareToPlay()
            player.play()
            previewPlayer = player
            playingFileName = track.fileName
        } catch {
            previewPlayer = nil
            playingFileName = nil
        }
    }

    private func stopPreview() {
        previewPlayer?.stop()
        previewPlayer = nil
        playingFileName = nil
    }

    // MARK: - Import New File(s)

    // 多選音樂檔 -> 逐一複製、分析曲長/BPM -> 一次建立 N 個段落（Layer 1 第 3、4、7 項，
    // Layer 2 沿用同一套邏輯，只是把入口移進音樂庫裡的「＋匯入新檔」）。
    private func handleImportedMusic(_ result: Result<[URL], Error>) {
        let urls: [URL]
        do {
            urls = try result.get()
        } catch {
            onImportFailed([error.localizedDescription])
            return
        }
        guard !urls.isEmpty else { return }

        isImporting = true
        Task { @MainActor in
            let musicDir = SQLiteDatabase.shared.musicDirectoryURL
            var existingNames = Set(
                (try? FileManager.default.contentsOfDirectory(atPath: musicDir.path)) ?? []
            )
            var importedTracks: [ImportedTrackInfo] = []
            var failedLabels: [String] = []

            for url in urls {
                let displayName = url.lastPathComponent
                let didAccess = url.startAccessingSecurityScopedResource()
                defer { if didAccess { url.stopAccessingSecurityScopedResource() } }

                let fileName = resolveUniqueMusicFileName(displayName, existingNames: existingNames)
                existingNames.insert(fileName)
                let destURL = musicDir.appendingPathComponent(fileName)

                do {
                    try? FileManager.default.removeItem(at: destURL)
                    try FileManager.default.copyItem(at: url, to: destURL)
                } catch {
                    failedLabels.append(displayName)
                    continue
                }

                let (_, durationMs, bpm) = await WaveformAnalyzer.shared.analyzeWaveform(for: fileName)
                importedTracks.append(ImportedTrackInfo(fileName: fileName, durationMs: durationMs, bpm: bpm))
            }

            isImporting = false
            reload()

            if !importedTracks.isEmpty {
                let newSegments = buildSegmentsForImportedTracks(
                    tracks: importedTracks,
                    classId: classId,
                    startOrderIndex: startOrderIndex
                )
                onSegmentsCreated(newSegments)
            }
            if !failedLabels.isEmpty {
                onImportFailed(failedLabels)
            }
            // 全部成功才自動關閉；有失敗時留在音樂庫讓教練看得到錯誤、也能重新挑選。
            if !importedTracks.isEmpty && failedLabels.isEmpty {
                onDismiss()
            }
        }
    }
}
