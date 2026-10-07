import SwiftUI
import UIKit

private let rateSwipeThreshold: CGFloat = 60

private let segmentSwipeThreshold: CGFloat = 80

func rateStepForSwipe(dx: CGFloat, dy: CGFloat, threshold: CGFloat) -> Int {
    let absDx = abs(dx)
    let absDy = abs(dy)
    if absDy > absDx { return 0 }
    if absDx <= threshold { return 0 }
    return dx > 0 ? 1 : -1
}

func segmentStepForSwipe(dx: CGFloat, dy: CGFloat, threshold: CGFloat) -> Int {
    let absDx = abs(dx)
    let absDy = abs(dy)
    if absDy <= absDx * 1.5 { return 0 }
    if absDy <= threshold { return 0 }
    return dy > 0 ? 1 : -1
}

func hudScale(availableHeight: CGFloat) -> CGFloat {
    min(max(availableHeight / 800, 0.7), 1.6)
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
    @State private var countdownPulse: CGFloat = 1.0

    private let reminderTimer = Timer.publish(every: 4.0, on: .main, in: .common).autoconnect()

    public init(workoutClass: WorkoutClass) {
        self.workoutClass = workoutClass
    }

    private var liveClass: WorkoutClass {
        audioManager.currentClass ?? workoutClass
    }

    private var activeSegment: WorkoutSegment? {
        audioManager.currentSegment ?? liveClass.segments.first
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
        let priorMs = liveClass.segments.prefix(audioManager.currentSegmentIndex).reduce(0) { $0 + $1.durationMs }
        return (priorMs / 1000) + Int(audioManager.currentOffsetSeconds)
    }

    private var totalClassSeconds: Int {
        if liveClass.totalDurationMs > 0 {
            return liveClass.totalDurationMs / 1000
        }
        return liveClass.segments.reduce(0) { $0 + $1.durationMs } / 1000
    }

    private var realtimeCalories: Int {
        guard totalClassSeconds > 0 else { return 0 }
        let ratio = min(1.0, max(0.0, Double(totalElapsedSeconds) / Double(totalClassSeconds)))
        return Int(liveClass.estimatedCalories * ratio)
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
            let hudHeight = geo.size.height - 60
            let scale = hudScale(availableHeight: hudHeight)

            VStack(spacing: 0) {
                cockpitHeader

                if isLandscape {
                    HStack(spacing: 0) {
                        if isPlaylistDrawerOpen {
                            playlistDrawer(scale: scale)
                                .frame(width: geo.size.width * 0.28)
                                .transition(.move(edge: .leading))
                            Divider()
                        }

                        cockpitCore(isLandscape: true, scale: scale, hudHeight: hudHeight)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                } else {
                    VStack(spacing: 0) {
                        cockpitCore(isLandscape: false, scale: scale, hudHeight: hudHeight)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                }
            }
            .background(FitnessRiderTheme.canvasWhite)
            .ignoresSafeArea(.keyboard)
            .onAppear {
                if AppSettings.shared.keepScreenAwakeInHUD {
                    UIApplication.shared.isIdleTimerDisabled = true
                }
                withAnimation(.easeInOut(duration: 0.4).repeatForever(autoreverses: true)) {
                    pulseAlpha = 0.35
                }
                audioManager.loadClass(workoutClass)
                audioManager.play()
                TrackDurationRepair.shared.start(segments: liveClass.segments)
            }
            .onDisappear {
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

    private var cockpitHeader: some View {
        HStack(spacing: 16) {
            Button {
                isShowingExitAlert = true
            } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.white)
            }

            Button {
                withAnimation(.easeInOut(duration: 0.2)) {
                    isPlaylistDrawerOpen.toggle()
                }
            } label: {
                Image(systemName: "sidebar.left")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(liveClass.title)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
                    .lineLimit(1)

                Text("即時消耗: \(realtimeCalories) / \(Int(liveClass.estimatedCalories)) kcal")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(.white.opacity(0.9))
            }

            Spacer()

            HStack(spacing: 12) {
                Button {
                    audioManager.previousSegment()
                } label: {
                    Image(systemName: "backward.fill")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundColor(.white)
                }

                Button {
                    audioManager.seekBy(deltaSeconds: -10.0)
                } label: {
                    Image(systemName: "gobackward.10")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundColor(.white)
                }

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

                Button {
                    audioManager.seekBy(deltaSeconds: 10.0)
                } label: {
                    Image(systemName: "goforward.10")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundColor(.white)
                }

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

    private func playlistDrawer(scale: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("課堂曲目清單")
                    .font(.system(size: 18 * scale, weight: .bold))
                    .foregroundColor(FitnessRiderTheme.textPrimary)
                Spacer()
                Text("\(liveClass.segments.count) 首")
                    .font(.system(size: 14 * scale, weight: .semibold))
                    .foregroundColor(FitnessRiderTheme.textSecondary)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(FitnessRiderTheme.cardHeaderBackground)

            Divider()

            ScrollView {
                LazyVStack(spacing: 4) {
                    ForEach(Array(liveClass.segments.enumerated()), id: \.element.id) { index, segment in
                        let isCurrent = index == audioManager.currentSegmentIndex

                        HStack(spacing: 12) {
                            Text("\(index + 1)")
                                .font(.system(size: 14 * scale, weight: .bold))
                                .foregroundColor(isCurrent ? .white : FitnessRiderTheme.textSecondary)
                                .frame(width: 28 * scale, height: 28 * scale)
                                .background(isCurrent ? FitnessRiderTheme.topBarGreenDark : Color.clear)
                                .clipShape(Circle())

                            VStack(alignment: .leading, spacing: 2) {
                                Text(segment.title)
                                    .font(.system(size: 18 * scale, weight: isCurrent ? .bold : .semibold))
                                    .foregroundColor(isCurrent ? FitnessRiderTheme.topBarGreenDark : FitnessRiderTheme.textPrimary)
                                    .lineLimit(1)

                                HStack(spacing: 8) {
                                    Text(segment.formattedDuration)
                                    Text("•")
                                    Text("\(Int(segment.effectiveBpm)) BPM")
                                }
                                .font(.system(size: 14 * scale))
                                .foregroundColor(FitnessRiderTheme.textSecondary)
                                .lineLimit(1)
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
                            audioManager.jumpToSegment(index)
                            audioManager.play()
                        }
                    }
                }
                .padding(8)
            }
        }
        .background(FitnessRiderTheme.cardBackground)
    }

    private func cockpitCore(isLandscape: Bool, scale: CGFloat, hudHeight: CGFloat) -> some View {
        VStack(spacing: 16) {
            Spacer()

            if isLandscape {
                HStack(alignment: .bottom, spacing: 28) {
                    handPositionCockpitCard(scale: scale)
                    circleProgressGauge(isLandscape: true, scale: scale, hudHeight: hudHeight)
                    VStack(spacing: 8) {
                        totalElapsedTimeBadge(scale: scale)
                        postureFigureCockpitCard(scale: scale)
                    }
                }
            } else {
                VStack(spacing: 8) {
                    totalElapsedTimeBadge(scale: scale)
                    circleProgressGauge(isLandscape: false, scale: scale, hudHeight: hudHeight)
                }
            }

            coachingPromptBanner(scale: scale)

            HStack(spacing: 20) {
                HStack(spacing: 8) {
                    HStack(spacing: 6) {
                        Image(systemName: "gauge.with.needle.fill")
                        Text(activeCue?.resistanceLevel ?? "LEVEL 5")
                    }
                    .font(.system(size: 18 * scale, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(FitnessRiderTheme.topBarGreenDark)
                    .cornerRadius(20)

                    if let delta = resistanceDelta {
                        Text(delta.text)
                            .font(.system(size: 16 * scale, weight: .bold))
                            .foregroundColor(delta.isUp ? FitnessRiderTheme.accentRed : FitnessRiderTheme.topBarGreenDark)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(delta.isUp ? FitnessRiderTheme.accentRed.opacity(0.15) : FitnessRiderTheme.topBarGreen.opacity(0.15))
                            .cornerRadius(12)
                    }
                }

                HStack(spacing: 6) {
                    Button("-2%") {
                        audioManager.adjustRatePercent(by: -2.0)
                    }
                    .buttonStyle(HUDTempoButtonStyle(fontSize: 16 * scale))

                    Button("100%") {
                        audioManager.resetRate()
                    }
                    .buttonStyle(HUDTempoButtonStyle(isPrimary: true, fontSize: 16 * scale))

                    Button("+2%") {
                        audioManager.adjustRatePercent(by: 2.0)
                    }
                    .buttonStyle(HUDTempoButtonStyle(fontSize: 16 * scale))

                    Text(String(format: "%.0f%%", audioManager.currentRate * 100))
                        .font(.system(size: 18 * scale, weight: .bold, design: .monospaced))
                        .foregroundColor(FitnessRiderTheme.textSecondary)
                }
            }

            Spacer()

            nextCueBanner(scale: scale)
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 16)
        .gesture(
            DragGesture(minimumDistance: 10)
                .onEnded { value in
                    let rateStep = rateStepForSwipe(
                        dx: value.translation.width,
                        dy: value.translation.height,
                        threshold: rateSwipeThreshold
                    )
                    if rateStep != 0 {
                        audioManager.adjustRatePercent(by: Double(rateStep) * 2.0)
                        HapticFeedbackManager.shared.playCountdownTick()
                        return
                    }
                    let segmentStep = segmentStepForSwipe(
                        dx: value.translation.width,
                        dy: value.translation.height,
                        threshold: segmentSwipeThreshold
                    )
                    if segmentStep > 0 {
                        audioManager.nextSegment()
                    } else if segmentStep < 0 {
                        audioManager.previousSegment()
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

    private func circleProgressGauge(isLandscape: Bool, scale: CGFloat, hudHeight: CGFloat) -> some View {
        let dialSize = (isLandscape ? 340 : 300) * scale
        return CircleProgressBar(
            progress: progressRatio,
            strokeWidth: 18.0 * scale,
            ringColor: currentZoneColor,
            trackColor: FitnessRiderTheme.cardBorder
        ) {
            if remainingCueSeconds <= 5 {
                Text("\(remainingCueSeconds)")
                    .font(.system(size: hudHeight * 0.25, weight: .black, design: .monospaced))
                    .foregroundColor(FitnessRiderTheme.accentRed)
                    .minimumScaleFactor(0.5)
                    .lineLimit(1)
                    .scaleEffect(countdownPulse)
            } else {
                VStack(spacing: 4) {
                    Text("ZONE \(activeSegment?.intensityZone ?? 2)")
                        .font(.system(size: 14 * scale, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 2)
                        .background(currentZoneColor)
                        .cornerRadius(12)

                    Text("目標轉速")
                        .font(.system(size: 16 * scale, weight: .bold))
                        .foregroundColor(FitnessRiderTheme.textSecondary)

                    HStack(alignment: .firstTextBaseline, spacing: 4) {
                        Text("\(activeCue?.targetRpm ?? 85)")
                            .font(.system(size: 82 * scale, weight: .black, design: .rounded))
                            .foregroundColor(FitnessRiderTheme.textPrimary)

                        Text("RPM")
                            .font(.system(size: 24 * scale, weight: .bold))
                            .foregroundColor(FitnessRiderTheme.textSecondary)
                    }

                    if let segment = activeSegment {
                        HStack(spacing: 4) {
                            Image(systemName: "metronome.fill")
                            Text(String(format: "%.0f BPM", segment.effectiveBpm))
                        }
                        .font(.system(size: 18 * scale, weight: .semibold))
                        .foregroundColor(FitnessRiderTheme.textSecondary)
                    }

                    let minutes = remainingCueSeconds / 60
                    let seconds = remainingCueSeconds % 60
                    Text(String(format: "%02d:%02d", minutes, seconds))
                        .font(.system(size: 64 * scale, weight: .bold, design: .monospaced))
                        .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                }
            }
        }
        .frame(width: dialSize, height: dialSize)
        .contentShape(Circle())
        .onTapGesture(count: 2) {
            audioManager.togglePlayPause()
        }
        .onChange(of: remainingCueSeconds) { newValue in
            guard newValue <= 5 else { return }
            countdownPulse = 1.4
            withAnimation(.easeOut(duration: 0.8)) {
                countdownPulse = 1.0
            }
        }
    }

    private func totalElapsedTimeBadge(scale: CGFloat) -> some View {
        HStack(spacing: 6) {
            ZStack {
                Circle()
                    .fill(FitnessRiderTheme.topBarGreen.opacity(0.15))
                    .frame(width: 28 * scale, height: 28 * scale)
                Image(systemName: "timer")
                    .font(.system(size: 15 * scale, weight: .bold))
                    .foregroundColor(FitnessRiderTheme.topBarGreenDark)
            }

            VStack(alignment: .leading, spacing: 1) {
                Text("課程時間")
                    .font(.system(size: 14 * scale, weight: .semibold))
                    .foregroundColor(FitnessRiderTheme.textSecondary)

                HStack(alignment: .lastTextBaseline, spacing: 2) {
                    Text(formattedTotalElapsed)
                        .font(.system(size: 20 * scale, weight: .black, design: .monospaced))
                        .foregroundColor(FitnessRiderTheme.textPrimary)

                    Text("/ \(formattedTotalDuration)")
                        .font(.system(size: 14 * scale, weight: .medium, design: .monospaced))
                        .foregroundColor(FitnessRiderTheme.textSecondary)
                }
            }
        }
        .frame(width: 216 * scale)
        .padding(.vertical, 6)
        .background(Color.white)
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(FitnessRiderTheme.cardBorder, lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.06), radius: 3, x: 0, y: 1)
    }

    private func handPositionCockpitCard(scale: CGFloat) -> some View {
        VStack(spacing: 8) {
            Text("握把把位")
                .font(.system(size: 16 * scale, weight: .bold))
                .foregroundColor(FitnessRiderTheme.textSecondary)

            if let cue = activeCue {
                Image(cue.handPosition.assetImageName)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 120 * scale, height: 120 * scale)
            } else {
                Circle()
                    .fill(FitnessRiderTheme.cardBorder.opacity(0.3))
                    .frame(width: 120 * scale, height: 120 * scale)
            }

            Text(activeCue?.handPosition.shortTitle ?? "1 號位")
                .font(.system(size: 30 * scale, weight: .heavy))
                .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                .lineLimit(1)
                .minimumScaleFactor(0.6)

            Text(activeCue?.handPosition == .position1 ? "平把中段" : (activeCue?.handPosition == .position2 ? "橫桿轉折" : "前端牛角"))
                .font(.system(size: 16 * scale, weight: .bold))
                .foregroundColor(FitnessRiderTheme.textPrimary)
                .lineLimit(1)

            Text(activeCue?.handPosition == .position1 ? "雙手放近身平把" : (activeCue?.handPosition == .position2 ? "手握橫桿轉折處" : "雙手扣住前端牛角"))
                .font(.system(size: 14 * scale))
                .foregroundColor(FitnessRiderTheme.textSecondary)
                .lineLimit(1)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 14)
        .frame(width: 216 * scale)
        .background(Color.white)
        .cornerRadius(16)
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(FitnessRiderTheme.cardBorder, lineWidth: 1))
        .shadow(color: Color.black.opacity(0.06), radius: 4, x: 0, y: 2)
    }

    private func postureFigureCockpitCard(scale: CGFloat) -> some View {
        VStack(spacing: 8) {
            Button {
                isShowingPostureInfoSheet = true
            } label: {
                HStack(spacing: 4) {
                    Text("騎乘姿勢")
                        .font(.system(size: 16 * scale, weight: .bold))
                        .foregroundColor(FitnessRiderTheme.textSecondary)
                    Image(systemName: "info.circle.fill")
                        .font(.system(size: 15 * scale))
                        .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                }
            }

            if let cue = activeCue {
                Image(cue.posture.assetImageName)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 120 * scale, height: 120 * scale)
            } else {
                Circle()
                    .fill(FitnessRiderTheme.cardBorder.opacity(0.3))
                    .frame(width: 120 * scale, height: 120 * scale)
            }

            Text(activeCue?.posture.localizedName ?? "坐姿平路")
                .font(.system(size: 30 * scale, weight: .heavy))
                .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                .lineLimit(1)
                .minimumScaleFactor(0.6)

            Text("建議 \(activeCue?.targetRpm ?? 85) RPM")
                .font(.system(size: 16 * scale, weight: .bold))
                .foregroundColor(FitnessRiderTheme.textPrimary)
                .lineLimit(1)

            Text(activeCue?.posture == .seatedFlat ? "基礎體能建立" : (activeCue?.posture == .standingFlat ? "核心穩定鍛鍊" : (activeCue?.posture == .seatedClimb ? "臀腿阻力爬坡" : (activeCue?.posture == .standingClimb ? "重阻力站立攀登" : (activeCue?.posture == .jumps ? "動態抽車跳躍" : (activeCue?.posture == .sprint ? "極限全力衝刺" : "緩和放鬆心率"))))))
                .font(.system(size: 14 * scale))
                .foregroundColor(FitnessRiderTheme.textSecondary)
                .lineLimit(1)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 14)
        .frame(width: 216 * scale)
        .background(Color.white)
        .cornerRadius(16)
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(FitnessRiderTheme.cardBorder, lineWidth: 1))
        .shadow(color: Color.black.opacity(0.06), radius: 4, x: 0, y: 2)
    }

    private func coachingPromptBanner(scale: CGFloat) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "quote.bubble.fill")
                .font(.system(size: 20 * scale))
                .foregroundColor(FitnessRiderTheme.topBarGreenDark)

            Text(currentCoachingPrompt)
                .font(.system(size: 20 * scale, weight: .bold))
                .foregroundColor(FitnessRiderTheme.textPrimary)
                .lineLimit(2)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(FitnessRiderTheme.topBarGreen.opacity(0.12))
        .cornerRadius(20)
    }

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

    private func nextCueBanner(scale: CGFloat) -> some View {
        let isWarning = remainingCueSeconds <= 5 && nextCue != nil
        return HStack(spacing: 12) {
            Image(systemName: "arrow.right.circle.fill")
                .font(.system(size: 26 * scale, weight: .bold))
                .foregroundColor(isWarning ? FitnessRiderTheme.accentRed : FitnessRiderTheme.topBarGreenDark)

            if let next = nextCue {
                HStack(spacing: 6) {
                    Text("下一動作:")
                        .font(.system(size: 20 * scale, weight: .bold))
                        .foregroundColor(FitnessRiderTheme.textPrimary)
                        .lineLimit(1)

                    Text(next.posture.localizedName)
                        .font(.system(size: 20 * scale, weight: .black))
                        .foregroundColor(FitnessRiderTheme.textPrimary)
                        .lineLimit(1)

                    Text("(\(next.targetRpm) RPM, \(next.resistanceLevel), \(next.handPosition.shortTitle))")
                        .font(.system(size: 17 * scale, weight: .semibold))
                        .foregroundColor(isWarning ? FitnessRiderTheme.accentRed : FitnessRiderTheme.topBarGreenDark)
                        .lineLimit(1)
                        .truncationMode(.tail)

                    Spacer()

                    Text("\(remainingCueSeconds) 秒後轉換")
                        .font(.system(size: 20 * scale, weight: .bold, design: .monospaced))
                        .lineLimit(1)
                        .foregroundColor(isWarning ? FitnessRiderTheme.accentRed : FitnessRiderTheme.topBarGreenDark)
                }
            } else {
                Text("此段落最後動作，堅持踩到底！")
                    .font(.system(size: 20 * scale, weight: .bold))
                    .foregroundColor(FitnessRiderTheme.textPrimary)
                    .lineLimit(1)
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

struct HUDTempoButtonStyle: ButtonStyle {
    var isPrimary: Bool = false
    var fontSize: CGFloat = 14

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: fontSize, weight: .bold))
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

