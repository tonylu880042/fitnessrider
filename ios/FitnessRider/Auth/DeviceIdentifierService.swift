import Foundation
import UIKit
import Security

public final class DeviceIdentifierService: Sendable {
    public static let shared = DeviceIdentifierService()

    private let keychainKey = "app.fitnessrider.device_fingerprint"

    public static let keychainTrialStartKey = "app.fitnessrider.trial_start_timestamp"
    public static let keychainTrialLockedKey = "app.fitnessrider.trial_permanently_locked"
    public static let keychainVipLicenseKey = "app.fitnessrider.vip_license_key"
    public static let keychainVipExpiresKey = "app.fitnessrider.vip_expires_timestamp"
    public static let keychainDeviceSecretKey = "app.fitnessrider.device_secret"

    private init() {}

    public var deviceFingerprint: String {
        if let stored = getKeychainString(key: keychainKey) {
            return stored
        }

        let newId = UIDevice.current.identifierForVendor?.uuidString ?? UUID().uuidString
        setKeychainString(key: keychainKey, value: newId)
        return newId
    }

    public var deviceModel: String {
        return UIDevice.current.model
    }

    // MARK: - Trial & VIP Keychain Accessors

    public var trialStartTimestamp: TimeInterval? {
        get {
            guard let str = getKeychainString(key: Self.keychainTrialStartKey),
                  let interval = TimeInterval(str) else {
                return nil
            }
            return interval
        }
        set {
            if let val = newValue {
                setKeychainString(key: Self.keychainTrialStartKey, value: String(val))
            } else {
                deleteKeychainString(key: Self.keychainTrialStartKey)
            }
        }
    }

    public var isTrialPermanentlyLocked: Bool {
        get {
            return getKeychainString(key: Self.keychainTrialLockedKey) == "true"
        }
        set {
            if newValue {
                setKeychainString(key: Self.keychainTrialLockedKey, value: "true")
            } else {
                deleteKeychainString(key: Self.keychainTrialLockedKey)
            }
        }
    }

    public var vipLicenseKey: String? {
        get {
            return getKeychainString(key: Self.keychainVipLicenseKey)
        }
        set {
            if let val = newValue {
                setKeychainString(key: Self.keychainVipLicenseKey, value: val)
            } else {
                deleteKeychainString(key: Self.keychainVipLicenseKey)
            }
        }
    }

    public var vipExpiresTimestamp: TimeInterval? {
        get {
            guard let str = getKeychainString(key: Self.keychainVipExpiresKey),
                  let interval = TimeInterval(str) else {
                return nil
            }
            return interval
        }
        set {
            if let val = newValue {
                setKeychainString(key: Self.keychainVipExpiresKey, value: String(val))
            } else {
                deleteKeychainString(key: Self.keychainVipExpiresKey)
            }
        }
    }

    /// 裝置專屬密鑰：裝置第一次成功開通授權/推廣代碼時，後端 /api/license/activate（或
    /// /api/device/transfer）回傳的裝置專屬密鑰，之後呼叫 /api/license/verify 時要用它
    /// 簽章請求，避免任何人只憑猜到的 device_fingerprint（identifierForVendor 並非秘密）
    /// 就能查詢這台裝置的真實授權狀態（spec 項目 E）。見 LicenseVerificationService。
    public var deviceSecret: String? {
        get {
            return getKeychainString(key: Self.keychainDeviceSecretKey)
        }
        set {
            if let val = newValue {
                setKeychainString(key: Self.keychainDeviceSecretKey, value: val)
            } else {
                deleteKeychainString(key: Self.keychainDeviceSecretKey)
            }
        }
    }

    // MARK: - Generic Keychain Operations

    public func getKeychainString(key: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
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

    public func setKeychainString(key: String, value: String) {
        guard let data = value.data(using: .utf8) else { return }

        // Remove old
        deleteKeychainString(key: key)

        // Add new
        let addQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
        SecItemAdd(addQuery as CFDictionary, nil)
    }

    public func deleteKeychainString(key: String) {
        let delQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key
        ]
        SecItemDelete(delQuery as CFDictionary)
    }
}
