import SwiftUI
import UIKit

// HUD 播放中「右滑加速、左滑減速」手勢：水平位移須超過此門檻才算數（客戶回報開發清單 B）
private let rateSwipeThreshold: CGFloat = 60

/// 手勢位移 → 要不要調速、往哪調，抽成純函式方便測試。
/// 回傳 +1（右滑加速一階）、-1（左滑減速一階）、0（未達門檻或垂直位移較大，忽略）。
/// 一次滑動只前進一階，不做「滑越遠調越多」——與 Android 同名同行為。
func rateStepForSwipe(dx: CGFloat, dy: CGFloat, threshold: CGFloat) -> Int {
    let absDx = abs(dx)
    let absDy = abs(dy)
    if absDy > absDx { return 0 }
    if absDx <= threshold { return 0 }
    return dx > 0 ? 1 : -1
}

public struct WorkoutHUDView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var audioManager = AudioEngineManager.shared

    public let workoutClass: WorkoutClass

    @State private var isShowingExitAlert: Bool = false
    @State private var isPlaylistDrawerOpen: Bool = true
    @State private var isShowingPostureInfoSheet: Bool = false
    @State private var reminderRotationTick: Int = 0
    @State private var pulseAlpha: CGFloat = 1.0

    private let reminderTimer = Timer.publish(every: 4.0, on: .main, in: .common).autoconnect()

    public init(workoutClass: WorkoutClass) {
        self.workoutClass = workoutClass
    }

    private var activeSegment: WorkoutSegment? {
        audioManager.currentSegment ?? workoutClass.segments.first
    }

    private var currentZoneColor: Color {
        FitnessRiderTheme.colorForZone(activeSegment?.intensityZone ?? 2)
    }

    private var activeCue: WorkoutCue? {
        guard let segment = activeSegment else { return nil }
        let currentMs = Int(audioManager.currentOffsetSeconds * 1000)
        return segment.cues.last(where: { $0.offsetMs <= currentMs }) ?? segment.cues.first
    }

    private var nextCue: WorkoutCue? {
        guard let segment = activeSegment else { return nil }
        let currentMs = Int(audioManager.currentOffsetSeconds * 1000)
        return segment.cues.first(where: { $0.offsetMs > currentMs })
    }

    private var resistanceDelta: (text: String, isUp: Bool)? {
        guard let current = activeCue, let segment = activeSegment else { return nil }
        guard let currentIndex = segment.cues.firstIndex(where: { $0.id == current.id }), currentIndex > 0 else { return nil }
        let prev = segment.cues[currentIndex - 1]
        let currNum = Int(current.resistanceLevel.filter { $0.isNumber }) ?? 0
        let prevNum = Int(prev.resistanceLevel.filter { $0.isNumber }) ?? 0
        if currNum > prevNum {
            return ("▲ 阻力加重 (+1)", true)
        } else if currNum < prevNum {
            return ("▼ 阻力減輕 (-1)", false)
        }
        return nil
    }

    private var currentCoachingPrompt: String {
        guard let cue = activeCue else { return "專注踩踏，維持穩定節拍" }
        if !cue.reminders.isEmpty {
            let index = reminderRotationTick % cue.reminders.count
            return cue.reminders[index]
        }
        return cue.posture.trainingGoalDescription
    }

    private var remainingCueSeconds: Int {
        guard let next = nextCue else {
            // If no next cue, show remaining segment seconds
            return max(0, Int(audioManager.currentDurationSeconds - audioManager.currentOffsetSeconds))
        }
        let currentMs = Int(audioManager.currentOffsetSeconds * 1000)
        return max(0, (next.offsetMs - currentMs) / 1000)
    }

    private var progressRatio: Double {
        guard audioManager.currentDurationSeconds > 0 else { return 0.0 }
        return min(1.0, max(0.0, audioManager.currentOffsetSeconds / audioManager.currentDurationSeconds))
    }

    private var totalElapsedSeconds: Int {
        let priorMs = workoutClass.segments.prefix(audioManager.currentSegmentIndex).reduce(0) { $0 + $1.durationMs }
        return (priorMs / 1000) + Int(audioManager.currentOffsetSeconds)
    }

    private var totalClassSeconds: Int {
        if workoutClass.totalDurationMs > 0 {
            return workoutClass.totalDurationMs / 1000
        }
        return workoutClass.segments.reduce(0) { $0 + $1.durationMs } / 1000
    }

    private var realtimeCalories: Int {
        guard totalClassSeconds > 0 else { return 0 }
        let ratio = min(1.0, max(0.0, Double(totalElapsedSeconds) / Double(totalClassSeconds)))
        return Int(workoutClass.estimatedCalories * ratio)
    }

    private var formattedTotalElapsed: String {
        let clamped = max(0, min(totalElapsedSeconds, totalClassSeconds))
        let min = clamped / 60
        let sec = clamped % 60
        return String(format: "%02d:%02d", min, sec)
    }

    private var formattedTotalDuration: String {
        let min = totalClassSeconds / 60
        let sec = totalClassSeconds % 60
        return String(format: "%02d:%02d", min, sec)
    }

    public var body: some View {
        GeometryReader { geo in
            let isLandscape = geo.size.width > geo.size.height

            VStack(spacing: 0) {
                // Signature #84BF09 Cockpit Header
                cockpitHeader

                // Main Stage
                if isLandscape {
                    HStack(spacing: 0) {
                        // Left Track Drawer
                        if isPlaylistDrawerOpen {
                            playlistDrawer
                                .frame(width: geo.size.width * 0.28)
                                .transition(.move(edge: .leading))
                            Divider()
                        }

                        // Right Cockpit Core (Circle + Next Cue)
                        cockpitCore(isLandscape: true)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                } else {
                    // Portrait Adaptive Layout (for phone handlebar mount)
                    VStack(spacing: 0) {
                        cockpitCore(isLandscape: false)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                }
            }
            .background(FitnessRiderTheme.canvasWhite)
            .ignoresSafeArea(.keyboard)
            .onAppear {
                // Keep screen awake during workout execution
                if AppSettings.shared.keepScreenAwakeInHUD {
                    UIApplication.shared.isIdleTimerDisabled = true
                }
                withAnimation(.easeInOut(duration: 0.4).repeatForever(autoreverses: true)) {
                    pulseAlpha = 0.35
                }
                audioManager.loadClass(workoutClass)
                audioManager.play()
            }
            .onDisappear {
                // Restore normal screen sleep behavior
                UIApplication.shared.isIdleTimerDisabled = false
                audioManager.pause()
            }
            .alert("退出課堂確認", isPresented: $isShowingExitAlert) {
                Button("繼續騎乘", role: .cancel) {}
                Button("結束課堂", role: .destructive) {
                    audioManager.pause()
                    dismiss()
                }
            } message: {
                Text("確定要結束並退出課堂嗎？目前授課進度將不會儲存。")
            }
        }
    }

    // MARK: - Cockpit Header

    private var cockpitHeader: some View {
        HStack(spacing: 16) {
            // Exit Button
            Button {
                isShowingExitAlert = true
            } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.white)
            }

            // Playlist Drawer Toggle
            Button {
                withAnimation(.easeInOut(duration: 0.2)) {
                    isPlaylistDrawerOpen.toggle()
                }
            } label: {
                Image(systemName: "sidebar.left")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
            }

            // Title & Real-time Calorie Accumulator
            VStack(alignment: .leading, spacing: 2) {
                Text(workoutClass.title)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
                    .lineLimit(1)

                Text("即時消耗: \(realtimeCalories) / \(Int(workoutClass.estimatedCalories)) kcal")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(.white.opacity(0.9))
            }

            Spacer()

            // Playback Seek Bar & Controls
            HStack(spacing: 12) {
                // Previous Track
                Button {
                    audioManager.previousSegment()
                } label: {
                    Image(systemName: "backward.fill")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundColor(.white)
                }

                // Seek -10s
                Button {
                    audioManager.seekBy(deltaSeconds: -10.0)
                } label: {
                    Image(systemName: "gobackward.10")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundColor(.white)
                }

                // Play / Pause Giant Button
                Button {
                    audioManager.togglePlayPause()
                } label: {
                    Image(systemName: audioManager.isPlaying ? "pause.fill" : "play.fill")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                        .frame(width: 44, height: 44)
                        .background(Color.white)
                        .clipShape(Circle())
                        .shadow(radius: 3)
                }

                // Seek +10s
                Button {
                    audioManager.seekBy(deltaSeconds: 10.0)
                } label: {
                    Image(systemName: "goforward.10")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundColor(.white)
                }

                // Next Track
                Button {
                    audioManager.nextSegment()
                } label: {
                    Image(systemName: "forward.fill")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundColor(.white)
                }
            }
        }
        .padding(.horizontal, 20)
        .frame(height: 60)
        .background(FitnessRiderTheme.topBarGreen)
        .shadow(color: Color.black.opacity(0.12), radius: 3, x: 0, y: 2)
    }

    // MARK: - Playlist Drawer

    private var playlistDrawer: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("課堂曲目清單")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(FitnessRiderTheme.textPrimary)
                Spacer()
                Text("\(workoutClass.segments.count) 首")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(FitnessRiderTheme.textSecondary)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(FitnessRiderTheme.cardHeaderBackground)

            Divider()

            ScrollView {
                LazyVStack(spacing: 4) {
                    ForEach(Array(workoutClass.segments.enumerated()), id: \.element.id) { index, segment in
                        let isCurrent = index == audioManager.currentSegmentIndex

                        HStack(spacing: 12) {
                            Text("\(index + 1)")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(isCurrent ? .white : FitnessRiderTheme.textSecondary)
                                .frame(width: 24, height: 24)
                                .background(isCurrent ? FitnessRiderTheme.topBarGreenDark : Color.clear)
                                .clipShape(Circle())

                            VStack(alignment: .leading, spacing: 2) {
                                Text(segment.title)
                                    .font(.system(size: 14, weight: isCurrent ? .bold : .medium))
                                    .foregroundColor(isCurrent ? FitnessRiderTheme.topBarGreenDark : FitnessRiderTheme.textPrimary)
                                    .lineLimit(1)

                                HStack(spacing: 8) {
                                    Text(segment.formattedDuration)
                                    Text("•")
                                    Text("\(Int(segment.effectiveBpm)) BPM")
                                }
                                .font(.system(size: 11))
                                .foregroundColor(FitnessRiderTheme.textSecondary)
                            }

                            Spacer()

                            if isCurrent && audioManager.isPlaying {
                                Image(systemName: "waveform")
                                    .font(.system(size: 14))
                                    .foregroundColor(FitnessRiderTheme.topBarGreen)
                            }
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .background(isCurrent ? FitnessRiderTheme.topBarGreen.opacity(0.12) : Color.clear)
                        .cornerRadius(8)
                        .contentShape(Rectangle())
                        .onTapGesture {
                            audioManager.loadClass(workoutClass, startSegmentIndex: index)
                            audioManager.play()
                        }
                    }
                }
                .padding(8)
            }
        }
        .background(FitnessRiderTheme.cardBackground)
    }

    // MARK: - Cockpit Core (3-Column Layout: Hand Position Card | Circle Gauge | Posture Card)

    private func cockpitCore(isLandscape: Bool) -> some View {
        VStack(spacing: 16) {
            Spacer()

            if isLandscape {
                HStack(alignment: .bottom, spacing: 28) {
                    handPositionCockpitCard
                    circleProgressGauge(isLandscape: true)
                    VStack(spacing: 8) {
                        totalElapsedTimeBadge
                        postureFigureCockpitCard
                    }
                }
            } else {
                VStack(spacing: 8) {
                    totalElapsedTimeBadge
                    circleProgressGauge(isLandscape: false)
                }
            }

            // Coaching Live Prompt Banner
            coachingPromptBanner

            // Info Strip (Resistance & Tempo Controls)
            HStack(spacing: 20) {
                // Resistance Badge + Delta Indicator
                HStack(spacing: 8) {
                    HStack(spacing: 6) {
                        Image(systemName: "gauge.with.needle.fill")
                        Text(activeCue?.resistanceLevel ?? "LEVEL 5")
                    }
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(FitnessRiderTheme.topBarGreenDark)
                    .cornerRadius(20)

                    if let delta = resistanceDelta {
                        Text(delta.text)
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(delta.isUp ? FitnessRiderTheme.accentRed : FitnessRiderTheme.topBarGreenDark)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(delta.isUp ? FitnessRiderTheme.accentRed.opacity(0.15) : FitnessRiderTheme.topBarGreen.opacity(0.15))
                            .cornerRadius(12)
                    }
                }

                // Tempo Steppers (-2%, 100%, +2%)
                HStack(spacing: 6) {
                    Button("-2%") {
                        audioManager.adjustRatePercent(by: -2.0)
                    }
                    .buttonStyle(HUDTempoButtonStyle())

                    Button("100%") {
                        audioManager.resetRate()
                    }
                    .buttonStyle(HUDTempoButtonStyle(isPrimary: true))

                    Button("+2%") {
                        audioManager.adjustRatePercent(by: 2.0)
                    }
                    .buttonStyle(HUDTempoButtonStyle())

                    Text(String(format: "%.0f%%", audioManager.currentRate * 100))
                        .font(.system(size: 13, weight: .bold, design: .monospaced))
                        .foregroundColor(FitnessRiderTheme.textSecondary)
                }
            }

            Spacer()

            // Bottom Next Cue Preview Strip
            nextCueBanner
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 16)
        .gesture(
            DragGesture(minimumDistance: 40)
                .onEnded { value in
                    // Horizontal swipe: width displacement dominates height by at least 1.5x
                    if abs(value.translation.width) > abs(value.translation.height) * 1.5 {
                        if value.translation.width < -70 {
                            audioManager.nextSegment()
                        } else if value.translation.width > 70 {
                            audioManager.previousSegment()
                        }
                    }
                }
        )
        .onReceive(reminderTimer) { _ in
            reminderRotationTick += 1
        }
        .sheet(isPresented: $isShowingPostureInfoSheet) {
            postureInfoSheet
        }
    }

    private func circleProgressGauge(isLandscape: Bool) -> some View {
        CircleProgressBar(
            progress: progressRatio,
            strokeWidth: 18.0,
            ringColor: currentZoneColor,
            trackColor: FitnessRiderTheme.cardBorder
        ) {
            VStack(spacing: 4) {
                // Intensity Zone Badge
                Text("ZONE \(activeSegment?.intensityZone ?? 2)")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 2)
                    .background(currentZoneColor)
                    .cornerRadius(12)

                Text("目標轉速")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(FitnessRiderTheme.textSecondary)

                // GIANT Target RPM
                HStack(alignment: .firstTextBaseline, spacing: 4) {
                    Text("\(activeCue?.targetRpm ?? 85)")
                        .font(.system(size: 80, weight: .black, design: .rounded))
                        .foregroundColor(FitnessRiderTheme.textPrimary)

                    Text("RPM")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(FitnessRiderTheme.textSecondary)
                }

                // Music BPM
                if let segment = activeSegment {
                    HStack(spacing: 4) {
                        Image(systemName: "metronome.fill")
                        Text(String(format: "%.0f BPM", segment.effectiveBpm))
                    }
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(FitnessRiderTheme.textSecondary)
                }

                // Interval Countdown Timer
                let minutes = remainingCueSeconds / 60
                let seconds = remainingCueSeconds % 60
                Text(String(format: "%02d:%02d", minutes, seconds))
                    .font(.system(size: 32, weight: .bold, design: .monospaced))
                    .foregroundColor(remainingCueSeconds <= 5 ? FitnessRiderTheme.accentRed : FitnessRiderTheme.topBarGreen)
            }
        }
        .frame(width: isLandscape ? 300 : 250, height: isLandscape ? 300 : 250)
        .contentShape(Circle())
        .onTapGesture(count: 2) {
            audioManager.togglePlayPause()
        }
        // 右滑加速／左滑減速：手勢區限定在中央圓形儀表。
        //
        // 這裡一定要用 `.gesture`，不能用 `.simultaneousGesture` —— 後者的語意是
        // 「允許與其他手勢同時辨識」，父層 cockpitCore 上的曲目切換拖曳
        // （`DragGesture(minimumDistance: 40)`，水平位移 > 70pt 觸發）會跟著一起成立：
        // 在圓形儀表上水平滑超過 70pt 會同時變速「並」跳到上/下一首，課堂中直接毀掉一段。
        // 子視圖的 `.gesture` 對落在子視圖內的觸控本來就優先於祖先的 `.gesture`。
        // 雙擊播放/暫停不受影響：DragGesture 要位移 10pt 才辨識，雙擊沒有位移。
        .gesture(
            DragGesture(minimumDistance: 10)
                .onEnded { value in
                    let step = rateStepForSwipe(
                        dx: value.translation.width,
                        dy: value.translation.height,
                        threshold: rateSwipeThreshold
                    )
                    if step != 0 {
                        audioManager.adjustRatePercent(by: Double(step) * 2.0)
                        HapticFeedbackManager.shared.playCountdownTick()
                    }
                }
        )
    }

    private var totalElapsedTimeBadge: some View {
        HStack(spacing: 6) {
            ZStack {
                Circle()
                    .fill(FitnessRiderTheme.topBarGreen.opacity(0.15))
                    .frame(width: 22, height: 22)
                Image(systemName: "timer")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(FitnessRiderTheme.topBarGreenDark)
            }

            VStack(alignment: .leading, spacing: 1) {
                Text("課程時間")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundColor(FitnessRiderTheme.textSecondary)

                HStack(alignment: .lastTextBaseline, spacing: 2) {
                    Text(formattedTotalElapsed)
                        .font(.system(size: 14, weight: .black, design: .monospaced))
                        .foregroundColor(FitnessRiderTheme.textPrimary)

                    Text("/ \(formattedTotalDuration)")
                        .font(.system(size: 11, weight: .medium, design: .monospaced))
                        .foregroundColor(FitnessRiderTheme.textSecondary)
                }
            }
        }
        .frame(width: 170)
        .padding(.vertical, 6)
        .background(Color.white)
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(FitnessRiderTheme.cardBorder, lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.06), radius: 3, x: 0, y: 1)
    }

    private var handPositionCockpitCard: some View {
        VStack(spacing: 8) {
            Text("握把把位")
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(FitnessRiderTheme.textSecondary)

            if let cue = activeCue {
                Image(cue.handPosition.assetImageName)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 105, height: 105)
            } else {
                Circle()
                    .fill(FitnessRiderTheme.cardBorder.opacity(0.3))
                    .frame(width: 105, height: 105)
            }

            Text(activeCue?.handPosition.shortTitle ?? "1 號位")
                .font(.system(size: 17, weight: .heavy))
                .foregroundColor(FitnessRiderTheme.topBarGreenDark)

            Text(activeCue?.handPosition == .position1 ? "平把中段" : (activeCue?.handPosition == .position2 ? "橫桿轉折" : "前端牛角"))
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(FitnessRiderTheme.textPrimary)

            Text(activeCue?.handPosition == .position1 ? "雙手放近身平把" : (activeCue?.handPosition == .position2 ? "手握橫桿轉折處" : "雙手扣住前端牛角"))
                .font(.system(size: 11))
                .foregroundColor(FitnessRiderTheme.textSecondary)
                .lineLimit(1)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 14)
        .frame(width: 170)
        .background(Color.white)
        .cornerRadius(16)
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(FitnessRiderTheme.cardBorder, lineWidth: 1))
        .shadow(color: Color.black.opacity(0.06), radius: 4, x: 0, y: 2)
    }

    private var postureFigureCockpitCard: some View {
        VStack(spacing: 8) {
            Button {
                isShowingPostureInfoSheet = true
            } label: {
                HStack(spacing: 4) {
                    Text("騎乘姿勢")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(FitnessRiderTheme.textSecondary)
                    Image(systemName: "info.circle.fill")
                        .font(.system(size: 12))
                        .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                }
            }

            if let cue = activeCue {
                Image(cue.posture.assetImageName)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 105, height: 105)
            } else {
                Circle()
                    .fill(FitnessRiderTheme.cardBorder.opacity(0.3))
                    .frame(width: 105, height: 105)
            }

            Text(activeCue?.posture.localizedName ?? "坐姿平路")
                .font(.system(size: 17, weight: .heavy))
                .foregroundColor(FitnessRiderTheme.topBarGreenDark)

            Text("建議 \(activeCue?.targetRpm ?? 85) RPM")
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(FitnessRiderTheme.textPrimary)

            Text(activeCue?.posture == .seatedFlat ? "基礎體能建立" : (activeCue?.posture == .standingFlat ? "核心穩定鍛鍊" : (activeCue?.posture == .seatedClimb ? "臀腿阻力爬坡" : (activeCue?.posture == .standingClimb ? "重阻力站立攀登" : (activeCue?.posture == .jumps ? "動態抽車跳躍" : (activeCue?.posture == .sprint ? "極限全力衝刺" : "緩和放鬆心率"))))))
                .font(.system(size: 11))
                .foregroundColor(FitnessRiderTheme.textSecondary)
                .lineLimit(1)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 14)
        .frame(width: 170)
        .background(Color.white)
        .cornerRadius(16)
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(FitnessRiderTheme.cardBorder, lineWidth: 1))
        .shadow(color: Color.black.opacity(0.06), radius: 4, x: 0, y: 2)
    }

    // MARK: - Coaching Prompt Banner

    private var coachingPromptBanner: some View {
        HStack(spacing: 8) {
            Image(systemName: "quote.bubble.fill")
                .font(.system(size: 14))
                .foregroundColor(FitnessRiderTheme.topBarGreenDark)

            Text(currentCoachingPrompt)
                .font(.system(size: 15, weight: .semibold))
                .foregroundColor(FitnessRiderTheme.textPrimary)
                .lineLimit(1)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(FitnessRiderTheme.topBarGreen.opacity(0.12))
        .cornerRadius(20)
    }

    // MARK: - Posture Info Sheet

    private var postureInfoSheet: some View {
        NavigationView {
            VStack(alignment: .leading, spacing: 20) {
                if let cue = activeCue {
                    HStack(spacing: 14) {
                        Image(systemName: cue.posture.sfSymbol)
                            .font(.system(size: 36, weight: .bold))
                            .foregroundColor(FitnessRiderTheme.topBarGreenDark)

                        VStack(alignment: .leading, spacing: 4) {
                            Text(cue.posture.localizedName)
                                .font(.system(size: 22, weight: .bold))
                                .foregroundColor(FitnessRiderTheme.textPrimary)
                            Text("建議踏頻: \(cue.posture.defaultRpm) RPM")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundColor(FitnessRiderTheme.textSecondary)
                        }
                    }
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(FitnessRiderTheme.cardHeaderBackground)
                    .cornerRadius(12)

                    VStack(alignment: .leading, spacing: 8) {
                        Text("訓練目標與生理效益")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(FitnessRiderTheme.textPrimary)
                        Text(cue.posture.trainingGoalDescription)
                            .font(.system(size: 15))
                            .foregroundColor(FitnessRiderTheme.textSecondary)
                            .lineSpacing(4)
                    }
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.white)
                    .cornerRadius(12)
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(FitnessRiderTheme.cardBorder, lineWidth: 1))

                    VStack(alignment: .leading, spacing: 10) {
                        Text("握把把位指引")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(FitnessRiderTheme.textPrimary)

                        HStack(spacing: 12) {
                            HandPositionBadge(position: cue.handPosition, isCompact: false)
                            Text(cue.handPosition.gripDescription)
                                .font(.system(size: 13))
                                .foregroundColor(FitnessRiderTheme.textSecondary)
                        }
                    }
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.white)
                    .cornerRadius(12)
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(FitnessRiderTheme.cardBorder, lineWidth: 1))
                }

                Spacer()
            }
            .padding(20)
            .navigationTitle("姿勢教學說明")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("關閉") {
                        isShowingPostureInfoSheet = false
                    }
                }
            }
        }
    }

    // MARK: - Next Cue Preview Banner

    private var nextCueBanner: some View {
        let isWarning = remainingCueSeconds <= 5 && nextCue != nil
        return HStack(spacing: 12) {
            Image(systemName: "arrow.right.circle.fill")
                .font(.system(size: 22, weight: .bold))
                .foregroundColor(isWarning ? FitnessRiderTheme.accentRed : FitnessRiderTheme.topBarGreenDark)

            if let next = nextCue {
                HStack(spacing: 6) {
                    Text("下一動作:")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(FitnessRiderTheme.textSecondary)

                    Text(next.posture.localizedName)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(FitnessRiderTheme.textPrimary)

                    Text("(\(next.targetRpm) RPM, \(next.resistanceLevel), \(next.handPosition.shortTitle))")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(isWarning ? FitnessRiderTheme.accentRed : FitnessRiderTheme.topBarGreenDark)

                    Spacer()

                    Text("\(remainingCueSeconds) 秒後轉換")
                        .font(.system(size: 15, weight: .bold, design: .monospaced))
                        .foregroundColor(isWarning ? FitnessRiderTheme.accentRed : FitnessRiderTheme.topBarGreenDark)
                }
            } else {
                Text("此段落最後動作，堅持踩到底！")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(FitnessRiderTheme.textSecondary)
                Spacer()
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 14)
        .background(isWarning ? FitnessRiderTheme.accentRed.opacity(0.10 * pulseAlpha) : Color.white)
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(isWarning ? FitnessRiderTheme.accentRed.opacity(pulseAlpha) : FitnessRiderTheme.topBarGreen, lineWidth: 2)
        )
        .shadow(color: Color.black.opacity(0.06), radius: 6, x: 0, y: 2)
    }
}

// HUD Button Style
struct HUDTempoButtonStyle: ButtonStyle {
    var isPrimary: Bool = false

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 14, weight: .bold))
            .foregroundColor(isPrimary ? .white : FitnessRiderTheme.textPrimary)
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .frame(minWidth: 54, minHeight: 44)
            .background(isPrimary ? FitnessRiderTheme.topBarGreen : FitnessRiderTheme.cardBorder)
            .cornerRadius(8)
            .contentShape(Rectangle())
            .opacity(configuration.isPressed ? 0.7 : 1.0)
    }
}

