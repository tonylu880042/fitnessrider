import Foundation

enum AppVariant {
    static var isRider3D: Bool {
        Bundle.main.object(forInfoDictionaryKey: "FRIsRider3D") as? Bool ?? false
    }
}
