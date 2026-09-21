import Foundation
import UIKit
import Security

public final class DeviceIdentifierService: Sendable {
    public static let shared = DeviceIdentifierService()

    private let keychainKey = "app.fitnessrider.device_fingerprint"

    private init() {}

    public var deviceFingerprint: String {
        if let stored = getStoredFingerprint() {
            return stored
        }

        let newId = UIDevice.current.identifierForVendor?.uuidString ?? UUID().uuidString
        saveFingerprint(newId)
        return newId
    }

    public var deviceModel: String {
        return UIDevice.current.model
    }

    // MARK: - Keychain Operations

    private func getStoredFingerprint() -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: keychainKey,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess,
              let data = item as? Data,
              let string = String(data: data, encoding: .utf8) else {
            return nil
        }
        return string
    }

    private func saveFingerprint(_ fingerprint: String) {
        guard let data = fingerprint.data(using: .utf8) else { return }

        // Remove old
        let delQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: keychainKey
        ]
        SecItemDelete(delQuery as CFDictionary)

        // Add new
        let addQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: keychainKey,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
        SecItemAdd(addQuery as CFDictionary, nil)
    }
}
