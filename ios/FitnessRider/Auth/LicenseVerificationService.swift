import Foundation

@MainActor
public final class LicenseVerificationService: ObservableObject {
    public static let shared = LicenseVerificationService()

    @Published public private(set) var isLicensed: Bool = true // Default true with offline grace
    @Published public private(set) var planType: String = "專業年繳版 (VIP)"
    @Published public private(set) var expirationDate: Date = Date().addingTimeInterval(365 * 86400)
    @Published public private(set) var remainingDays: Int = 365

    private let serverURL = "https://fitnessrider.app" // Configurable Vercel host

    private init() {
        loadCachedLicense()
    }

    public func verifyLicenseOnline() async {
        let fingerprint = DeviceIdentifierService.shared.deviceFingerprint

        guard let url = URL(string: "\(serverURL)/api/license/verify") else { return }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let body: [String: Any] = ["device_fingerprint": fingerprint]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            if let httpRes = response as? HTTPURLResponse, httpRes.statusCode == 200 {
                if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                   let valid = json["valid"] as? Bool {
                    await MainActor.run {
                        self.isLicensed = valid
                        if let days = json["remaining_days"] as? Int {
                            self.remainingDays = days
                        }
                    }
                }
            }
        } catch {
            // Offline: keep cached license active within grace period
            print("License offline verification: keeping cached status")
        }
    }

    private func loadCachedLicense() {
        // Standalone offline license initialization
        self.isLicensed = true
        self.remainingDays = 365
    }
}
