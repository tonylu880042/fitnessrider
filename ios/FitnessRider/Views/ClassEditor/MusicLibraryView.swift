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

/// 同一套即時搜尋邏輯套用在 Layer 3 的外部資料夾項目上，比對顯示檔名子字串。
func filterExternalMusicEntries(_ entries: [ExternalMusicEntry], query: String) -> [ExternalMusicEntry] {
    let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !trimmed.isEmpty else { return entries }
    return entries.filter { $0.displayName.range(of: trimmed, options: .caseInsensitive) != nil }
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

/// Layer 3：從外部資料夾多選曲目 -> 直接建立 N 個段落，musicFileName 存
/// `"extfolder://" + 相對路徑`（`MusicSource.isExternal` 用來辨識來源）。時長／BPM 先用預設值，
/// 實際數值等教練在編輯畫面選到該段落時，由既有的 WaveformAnalyzer 分析並寫回
/// （跟 Layer 1 對新匯入檔案的處理是同一條路徑）。
func buildSegmentsFromExternalSelection(
    entries: [ExternalMusicEntry],
    classId: UUID,
    startOrderIndex: Int
) -> [WorkoutSegment] {
    let importInfos = entries.map {
        ImportedTrackInfo(
            fileName: MusicSource.externalPrefix + $0.relativePath,
            durationMs: 300_000,
            bpm: 128.0,
            displayTitle: musicTitleFromFileName($0.displayName)
        )
    }
    return buildSegmentsForImportedTracks(tracks: importInfos, classId: classId, startOrderIndex: startOrderIndex)
}

/// 複製匯入的音樂檔到 [destURL]。中途失敗時清掉半成品，不在 Music 目錄留下截斷檔佔用檔名
/// （CLAUDE.md「已知落差」第二項：匯入失敗時已寫入一半的檔案沒有清掉）。
@discardableResult
func copyMusicFileOrCleanup(from sourceURL: URL, to destURL: URL) -> Bool {
    do {
        try? FileManager.default.removeItem(at: destURL)
        try FileManager.default.copyItem(at: sourceURL, to: destURL)
        return true
    } catch {
        try? FileManager.default.removeItem(at: destURL)
        return false
    }
}

private func formatDuration(_ durationMs: Int) -> String {
    let totalSec = durationMs / 1000
    return String(format: "%02d:%02d", totalSec / 60, totalSec % 60)
}

/// Layer 2 + Layer 3：app 內音樂庫，用分頁清楚區分兩種音樂來源，避免教練搞混「哪些檔案在哪」：
/// - 「已匯入音樂庫」：複製進 `Music/` 目錄的曲目（Layer 2）。
/// - 「音樂資料夾」：教練指定的外部資料夾，直接讀取內容、完全不複製檔案進 App（Layer 3）。
///
/// 試聽沿用 AVFoundation（app 既有的音訊框架，`WaveformAnalyzer`／`AudioEngineManager` 都建構在它上面），
/// 用內建的 `AVAudioPlayer` 播放單一檔案，沒有引入任何新的播放器套件或依賴。
final class MusicLibraryAudioDelegate: NSObject, AVAudioPlayerDelegate, @unchecked Sendable {
    var onDidFinish: (@MainActor @Sendable () -> Void)?

    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor in
            self.onDidFinish?()
        }
    }
}

struct MusicLibraryView: View {
    let classId: UUID
    let startOrderIndex: Int
    let onDismiss: () -> Void
    let onSegmentsCreated: ([WorkoutSegment]) -> Void
    let onImportFailed: ([String]) -> Void

    @State private var selectedTab: Int = 0
    @State private var searchQuery: String = ""

    // Layer 2：已匯入音樂庫狀態
    @State private var allTracks: [MusicLibraryTrack] = []
    @State private var selectedFileNames: Set<String> = []
    @State private var isImporting: Bool = false
    @State private var isShowingFileImporter: Bool = false

    // Layer 3：外部資料夾狀態
    @State private var folderConfigured: Bool = ExternalMusicFolderStore.shared.isFolderConfigured()
    @State private var includeSubdirectories: Bool = ExternalMusicFolderStore.shared.includeSubdirectories()
    @State private var externalEntries: [ExternalMusicEntry] = []
    @State private var selectedExternalPaths: Set<String> = []
    @State private var isLoadingExternal: Bool = false
    @State private var externalFolderError: String?
    @State private var isShowingFolderImporter: Bool = false

    // 兩個分頁共用同一顆試聽 AVAudioPlayer，用同一個 key 空間避免互相干擾：
    // "lib:<fileName>" 代表已匯入音樂庫項目，"ext:<relativePath>" 代表外部資料夾項目。
    @State private var previewPlayer: AVAudioPlayer?
    @State private var playingKey: String?
    @State private var audioDelegate = MusicLibraryAudioDelegate()

    private var filteredTracks: [MusicLibraryTrack] {
        filterMusicLibraryTracks(allTracks, query: searchQuery)
    }
    private var filteredExternalEntries: [ExternalMusicEntry] {
        filterExternalMusicEntries(externalEntries, query: searchQuery)
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
                    if selectedTab == 0 {
                        Button {
                            isShowingFileImporter = true
                        } label: {
                            Label("匯入新檔", systemImage: "plus")
                                .font(.system(size: 15, weight: .bold))
                        }
                    } else {
                        Button {
                            isShowingFolderImporter = true
                        } label: {
                            Label(folderConfigured ? "更換資料夾" : "選擇資料夾", systemImage: "folder")
                                .font(.system(size: 15, weight: .bold))
                        }
                    }
                }
            )
            .overlay(
                Text("音樂庫")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
            )

            // 分頁：清楚區分「複製進 App 的曲目」與「外部資料夾曲目」，避免教練搞混哪些檔案在哪。
            Picker("", selection: $selectedTab) {
                Text("已匯入音樂庫").tag(0)
                Text("音樂資料夾").tag(1)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 16)
            .padding(.top, 10)

            // 搜尋列：比照舊版 FragDialogSelectMusic 的即時 filter，兩個分頁共用同一個搜尋框。
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

            if selectedTab == 0 {
                libraryTabContent
            } else {
                folderTabContent
            }

            Divider()

            Button {
                confirmSelection()
            } label: {
                Text(isImporting ? "匯入中..." : "加入 \(currentSelectionCount) 首到課表")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(currentSelectionCount == 0 || isImporting ? FitnessRiderTheme.textMuted : FitnessRiderTheme.topBarGreen)
                    .cornerRadius(10)
            }
            .disabled(currentSelectionCount == 0 || isImporting)
            .padding(16)
        }
        .background(FitnessRiderTheme.canvasWhite)
        .onAppear {
            audioDelegate.onDidFinish = {
                stopPreview()
            }
            reload()
            if folderConfigured { reloadExternalEntries() }
        }
        .onDisappear { stopPreview() }
        .onChange(of: includeSubdirectories) { _, newValue in
            ExternalMusicFolderStore.shared.setIncludeSubdirectories(newValue)
            reloadExternalEntries()
        }
        .fileImporter(
            isPresented: $isShowingFileImporter,
            allowedContentTypes: [UTType.audio, UTType.mp3, UTType.mpeg4Audio],
            allowsMultipleSelection: true
        ) { result in
            handleImportedMusic(result)
        }
        .fileImporter(
            isPresented: $isShowingFolderImporter,
            allowedContentTypes: [UTType.folder],
            allowsMultipleSelection: false
        ) { result in
            handleFolderPicked(result)
        }
    }

    private var currentSelectionCount: Int {
        selectedTab == 0 ? selectedFileNames.count : selectedExternalPaths.count
    }

    private func confirmSelection() {
        if selectedTab == 0 {
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
        } else {
            let selected = externalEntries.filter { selectedExternalPaths.contains($0.relativePath) }
            guard !selected.isEmpty else { return }
            let newSegments = buildSegmentsFromExternalSelection(
                entries: selected,
                classId: classId,
                startOrderIndex: startOrderIndex
            )
            stopPreview()
            onSegmentsCreated(newSegments)
            onDismiss()
        }
    }

    // MARK: - Layer 2 分頁

    private var libraryTabContent: some View {
        Group {
            if filteredTracks.isEmpty {
                Spacer()
                emptyState(
                    icon: "music.note",
                    message: allTracks.isEmpty ? "尚未匯入任何音樂，點右上角「匯入新檔」開始" : "找不到符合的曲目"
                )
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: 8) {
                        ForEach(filteredTracks) { track in
                            let isSelected = selectedFileNames.contains(track.fileName)
                            let isPlaying = playingKey == "lib:\(track.fileName)"
                            musicRow(
                                title: track.title,
                                subtitle: "\(formatDuration(track.durationMs)) ・ \(Int(track.bpm)) BPM",
                                isSelected: isSelected,
                                isPlaying: isPlaying,
                                onToggleSelect: {
                                    if isSelected {
                                        selectedFileNames.remove(track.fileName)
                                    } else {
                                        selectedFileNames.insert(track.fileName)
                                    }
                                },
                                onTogglePreview: { toggleLibraryPreview(track) }
                            )
                        }
                    }
                    .padding(.horizontal, 16)
                }
            }
        }
    }

    // MARK: - Layer 3 分頁

    private var folderTabContent: some View {
        VStack(spacing: 0) {
            if folderConfigured {
                // 說明目前列出的是外部資料夾內容，不佔用 App 儲存空間，跟上方分頁的「已匯入音樂庫」是不同來源。
                HStack {
                    Text("直接讀取資料夾內容，不會複製進 App")
                        .font(.system(size: 12))
                        .foregroundColor(FitnessRiderTheme.textSecondary)
                    Spacer()
                    Toggle("含子資料夾", isOn: $includeSubdirectories)
                        .toggleStyle(.switch)
                        .font(.system(size: 12))
                        .fixedSize()
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 4)
            }

            if !folderConfigured {
                Spacer()
                emptyState(icon: "folder", message: "尚未選擇音樂資料夾", actionLabel: "選擇資料夾") {
                    isShowingFolderImporter = true
                }
                Spacer()
            } else if let error = externalFolderError {
                Spacer()
                emptyState(icon: "exclamationmark.triangle", message: error, actionLabel: "重新選擇資料夾") {
                    isShowingFolderImporter = true
                }
                Spacer()
            } else if isLoadingExternal {
                Spacer()
                ProgressView()
                Spacer()
            } else if filteredExternalEntries.isEmpty {
                Spacer()
                emptyState(
                    icon: "music.note",
                    message: externalEntries.isEmpty ? "這個資料夾裡沒有找到音樂檔" : "找不到符合的曲目"
                )
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: 8) {
                        ForEach(filteredExternalEntries) { entry in
                            let isSelected = selectedExternalPaths.contains(entry.relativePath)
                            let isPlaying = playingKey == "ext:\(entry.relativePath)"
                            musicRow(
                                title: musicTitleFromFileName(entry.displayName),
                                subtitle: "外部資料夾",
                                isSelected: isSelected,
                                isPlaying: isPlaying,
                                onToggleSelect: {
                                    if isSelected {
                                        selectedExternalPaths.remove(entry.relativePath)
                                    } else {
                                        selectedExternalPaths.insert(entry.relativePath)
                                    }
                                },
                                onTogglePreview: { toggleExternalPreview(entry) }
                            )
                        }
                    }
                    .padding(.horizontal, 16)
                }
            }
        }
    }

    // MARK: - Shared Rows

    private func emptyState(icon: String, message: String, actionLabel: String? = nil, action: (() -> Void)? = nil) -> some View {
        VStack(spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 48))
                .foregroundColor(FitnessRiderTheme.textMuted)
            Text(message)
                .font(.system(size: 14))
                .foregroundColor(FitnessRiderTheme.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
            if let actionLabel = actionLabel, let action = action {
                Button(action: action) {
                    Text(actionLabel)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 10)
                        .background(FitnessRiderTheme.topBarGreen)
                        .cornerRadius(8)
                }
            }
        }
    }

    private func musicRow(
        title: String,
        subtitle: String,
        isSelected: Bool,
        isPlaying: Bool,
        onToggleSelect: @escaping () -> Void,
        onTogglePreview: @escaping () -> Void
    ) -> some View {
        HStack(spacing: 12) {
            Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                .font(.system(size: 20))
                .foregroundColor(isSelected ? FitnessRiderTheme.topBarGreen : FitnessRiderTheme.textMuted)

            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(FitnessRiderTheme.textPrimary)
                    .lineLimit(1)
                Text(subtitle)
                    .font(.system(size: 12))
                    .foregroundColor(FitnessRiderTheme.textSecondary)
            }

            Spacer()

            Button {
                onTogglePreview()
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
        .onTapGesture { onToggleSelect() }
    }

    // MARK: - Data (Layer 2)

    private func reload() {
        let musicDir = SQLiteDatabase.shared.musicDirectoryURL
        let fileNames = (try? FileManager.default.contentsOfDirectory(atPath: musicDir.path)) ?? []
        allTracks = buildMusicLibraryTracks(fileNames: fileNames) { fileName in
            guard let cached = ClassRepository.shared.fetchWaveform(for: fileName) else { return nil }
            return (cached.durationMs, cached.bpm)
        }
    }

    // MARK: - Data (Layer 3)

    private func reloadExternalEntries() {
        guard folderConfigured else {
            externalEntries = []
            return
        }
        isLoadingExternal = true
        externalFolderError = nil
        let includeSubdirs = includeSubdirectories
        DispatchQueue.global(qos: .userInitiated).async {
            let entries = ExternalMusicFolderStore.shared.withFileAccess(relativePath: "") { baseURL in
                listExternalMusicEntries(baseURL: baseURL, includeSubdirectories: includeSubdirs)
            }
            DispatchQueue.main.async {
                self.isLoadingExternal = false
                if let entries = entries {
                    self.externalEntries = entries
                } else {
                    self.externalEntries = []
                    self.externalFolderError = "資料夾存取已失效，請重新選擇資料夾"
                }
            }
        }
    }

    private func handleFolderPicked(_ result: Result<[URL], Error>) {
        guard let url = (try? result.get())?.first else { return }
        do {
            try ExternalMusicFolderStore.shared.persist(folderURL: url)
            folderConfigured = true
            externalFolderError = nil
            selectedExternalPaths = []
            reloadExternalEntries()
        } catch {
            externalFolderError = "資料夾存取已失效，請重新選擇資料夾"
        }
    }

    // MARK: - Preview Playback

    private func toggleLibraryPreview(_ track: MusicLibraryTrack) {
        let key = "lib:\(track.fileName)"
        if playingKey == key {
            stopPreview()
            return
        }
        stopPreview()
        let fileURL = SQLiteDatabase.shared.musicDirectoryURL.appendingPathComponent(track.fileName)
        guard FileManager.default.fileExists(atPath: fileURL.path) else { return }
        do {
            let player = try AVAudioPlayer(contentsOf: fileURL)
            player.delegate = audioDelegate
            player.prepareToPlay()
            player.play()
            previewPlayer = player
            playingKey = key
        } catch {
            previewPlayer = nil
            playingKey = nil
        }
    }

    private func toggleExternalPreview(_ entry: ExternalMusicEntry) {
        let key = "ext:\(entry.relativePath)"
        if playingKey == key {
            stopPreview()
            return
        }
        stopPreview()
        // AVAudioPlayer 在 security-scoped 存取視窗裡建立好之後，關閉視窗不影響後續播放
        // （系統的檔案描述子已經開好），所以只需要把 init 包在 withResolvedFileURL 裡。
        let player = MusicSource.withResolvedFileURL(for: MusicSource.externalPrefix + entry.relativePath) { url in
            try? AVAudioPlayer(contentsOf: url)
        }.flatMap { $0 }
        guard let player = player else { return }
        player.delegate = audioDelegate
        player.prepareToPlay()
        player.play()
        previewPlayer = player
        playingKey = key
    }

    private func stopPreview() {
        previewPlayer?.stop()
        previewPlayer = nil
        playingKey = nil
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

                guard copyMusicFileOrCleanup(from: url, to: destURL) else {
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
