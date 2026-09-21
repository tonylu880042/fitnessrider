import SwiftUI

public struct TopNavBar<Leading: View, Trailing: View>: View {
    public let title: String
    public let leading: Leading
    public let trailing: Trailing

    public init(
        title: String,
        @ViewBuilder leading: () -> Leading = { EmptyView() },
        @ViewBuilder trailing: () -> Trailing = { EmptyView() }
    ) {
        self.title = title
        self.leading = leading()
        self.trailing = trailing()
    }

    public var body: some View {
        HStack(spacing: 16) {
            leading
                .font(.system(size: 17, weight: .semibold))
                .foregroundColor(.white)

            Spacer()

            Text(title)
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(.white)
                .lineLimit(1)

            Spacer()

            trailing
                .font(.system(size: 17, weight: .semibold))
                .foregroundColor(.white)
        }
        .padding(.horizontal, 20)
        .frame(height: 56)
        .background(FitnessRiderTheme.topBarGreen)
        .shadow(color: Color.black.opacity(0.12), radius: 3, x: 0, y: 2)
    }
}
