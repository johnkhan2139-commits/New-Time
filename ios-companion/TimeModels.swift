import Foundation
import FirebaseFirestore

enum TimeFirebaseConfig {
    static let projectId = "gen-lang-client-0418089832"
    static let databaseId = "ai-studio-android-time-96f54aac-a0c4-4798-a38b-c20dc1a3ccc5"
    static let platform = "IPHONE"

    static func firestore() -> Firestore {
        Firestore.firestore(database: databaseId)
    }
}

struct UserDirectoryProfile: Codable, Identifiable {
    var id: String { userId }
    let userId: String
    var displayName: String
    var handle: String
    var bio: String
    var avatarBase64: String?
    var devicePlatform: String
    var online: Bool
    @ServerTimestamp var createdAt: Timestamp?
    @ServerTimestamp var updatedAt: Timestamp?
}

struct UserPrivateInfo: Codable {
    let userId: String
    var phoneNumber: String?
    var blockedUids: [String]
    @ServerTimestamp var createdAt: Timestamp?
    @ServerTimestamp var updatedAt: Timestamp?
}

struct OnlineChat: Codable, Identifiable {
    @DocumentID var id: String?
    var name: String
    var groupChat: Bool
    var createdBy: String
    var memberUids: [String]
    var memberNames: [String]
    var lastMessagePreview: String
    @ServerTimestamp var lastMessageAt: Timestamp?
    @ServerTimestamp var createdAt: Timestamp?
    @ServerTimestamp var updatedAt: Timestamp?
}

struct OnlineMessage: Codable, Identifiable {
    @DocumentID var id: String?
    var chatId: String
    var senderId: String
    var senderName: String
    var body: String
    var mediaType: String // "TEXT", "IMAGE", "VIDEO"
    var mediaBase64: String?
    var mediaNote: String?
    var memberUids: [String]
    @ServerTimestamp var createdAt: Timestamp?
}

struct CallSession: Codable, Identifiable {
    @DocumentID var id: String?
    var callerId: String
    var callerName: String
    var calleeId: String
    var calleeName: String
    var chatId: String?
    var callType: String // "AUDIO", "VIDEO"
    var status: String   // "RINGING", "ACTIVE", "DECLINED", "ENDED"
    var offerSdp: String?
    var answerSdp: String?
    var participantUids: [String]
    @ServerTimestamp var createdAt: Timestamp?
    @ServerTimestamp var updatedAt: Timestamp?
}

struct OnlineSafetyReport: Codable, Identifiable {
    @DocumentID var id: String?
    var reporterId: String
    var targetUserId: String
    var targetName: String
    var category: String
    var details: String
    var status: String
    @ServerTimestamp var createdAt: Timestamp?
    @ServerTimestamp var updatedAt: Timestamp?
}
