import SwiftUI

public enum FitnessRiderTheme {
    public static let topBarGreen = Color(red: 132 / 255.0, green: 191 / 255.0, blue: 9 / 255.0)
    public static let topBarGreenDark = Color(red: 110 / 255.0, green: 163 / 255.0, blue: 6 / 255.0)
    public static let topBarGreenLight = Color(red: 162 / 255.0, green: 214 / 255.0, blue: 54 / 255.0)

    public static let canvasWhite = Color.white
    public static let cardBackground = Color(red: 247 / 255.0, green: 249 / 255.0, blue: 251 / 255.0)
    public static let cardHeaderBackground = Color(red: 238 / 255.0, green: 242 / 255.0, blue: 246 / 255.0)
    public static let cardBorder = Color(red: 226 / 255.0, green: 232 / 255.0, blue: 240 / 255.0)

    public static let textPrimary = Color(red: 33 / 255.0, green: 37 / 255.0, blue: 41 / 255.0)
    public static let textSecondary = Color(red: 108 / 255.0, green: 117 / 255.0, blue: 125 / 255.0)
    public static let textMuted = Color(red: 173 / 255.0, green: 181 / 255.0, blue: 189 / 255.0)

    public static let accentRed = Color(red: 229 / 255.0, green: 62 / 255.0, blue: 62 / 255.0)
    public static let accentOrange = Color(red: 237 / 255.0, green: 137 / 255.0, blue: 54 / 255.0)
    public static let accentBlue = Color(red: 49 / 255.0, green: 130 / 255.0, blue: 206 / 255.0)

    public static let zone1 = Color(red: 66 / 255.0, green: 153 / 255.0, blue: 225 / 255.0)
    public static let zone2 = Color(red: 72 / 255.0, green: 187 / 255.0, blue: 120 / 255.0)
    public static let zone3 = Color(red: 236 / 255.0, green: 201 / 255.0, blue: 75 / 255.0)
    public static let zone4 = Color(red: 237 / 255.0, green: 137 / 255.0, blue: 54 / 255.0)
    public static let zone5 = Color(red: 229 / 255.0, green: 62 / 255.0, blue: 62 / 255.0)

    public static func colorForZone(_ zone: Int) -> Color {
        switch zone {
        case 1: return zone1
        case 2: return zone2
        case 3: return zone3
        case 4: return zone4
        case 5: return zone5
        default: return topBarGreen
        }
    }
}
