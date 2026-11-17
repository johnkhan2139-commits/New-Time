import SwiftUI
import FirebaseAuth
import FirebaseFirestore

@MainActor
final class TimeIOSViewModel: ObservableObject {
    @Published var directoryUsers: [UserDirectoryProfile] = []
    @Published var chats: [OnlineChat] = []
    @Published var errorMessage: String?

    private let db = TimeFirebaseConfig.firestore()
    private var usersListener: ListenerRegistration?
    private var chatsListener: ListenerRegistration?

    func syncProfileOnSignIn(uid: String, displayName: String, phoneNumber: String?) async {
        let userRef = db.collection("users").document(uid)
        let privateRef = userRef.collection("private").document("info")
        let cleanName = displayName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "TIME iPhone User" : displayName
        let handle = cleanName.lowercased().filter { $0.isLetter || $0.isNumber || $0 == "_" } + "_" + String(uid.prefix(4))

        do {
            let snap = try await userRef.getDocument()
            if !snap.exists {
                try await userRef.setData([
                    "userId": uid,
                    "displayName": cleanName,
                    "handle": handle,
                    "bio": "Available on TIME iPhone",
                    "devicePlatform": TimeFirebaseConfig.platform,
                    "online": true,
                    "createdAt": FieldValue.serverTimestamp(),
                    "updatedAt": FieldValue.serverTimestamp()
                ])
            } else {
                try await userRef.updateData([
                    "devicePlatform": TimeFirebaseConfig.platform,
                    "online": true,
                    "updatedAt": FieldValue.serverTimestamp()
                ])
            }

            let privSnap = try await privateRef.getDocument()
            if !privSnap.exists {
                var privData: [String: Any] = [
                    "userId": uid,
                    "blockedUids": [String](),
                    "createdAt": FieldValue.serverTimestamp(),
                    "updatedAt": FieldValue.serverTimestamp()
                ]
                if let phone = phoneNumber, !phone.isEmpty {
                    privData["phoneNumber"] = phone
                }
                try await privateRef.setData(privData)
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func startListeners(uid: String) {
        usersListener = db.collection("users")
            .limit(to: 100)
            .addSnapshotListener { [weak self] snapshot, error in
                if let error {
                    self?.errorMessage = error.localizedDescription
                    return
                }
                let users = snapshot?.documents.compactMap { try? $0.data(as: UserDirectoryProfile.self) } ?? []
                self?.directoryUsers = users.filter { $0.userId != uid }
            }

        chatsListener = db.collection("chats")
            .whereField("memberUids", arrayContains: uid)
            .limit(to: 100)
            .addSnapshotListener { [weak self] snapshot, error in
                if let error {
                    self?.errorMessage = error.localizedDescription
                    return
                }
                self?.chats = snapshot?.documents.compactMap { try? $0.data(as: OnlineChat.self) } ?? []
            }
    }

    func stopListeners() {
        usersListener?.remove()
        chatsListener?.remove()
    }
}

struct TimeAuthAndChatView: View {
    @StateObject private var vm = TimeIOSViewModel()
    @State private var currentUid: String? = Auth.auth().currentUser?.uid

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                if let uid = currentUid {
                    List {
                        Section("Online Friends (Android & iPhone)") {
                            ForEach(vm.directoryUsers) { user in
                                HStack {
                                    VStack(alignment: .leading) {
                                        Text(user.displayName).font(.headline)
                                        Text("@\(user.handle) • \(user.devicePlatform)")
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    Circle()
                                        .fill(user.online ? Color.green : Color.gray)
                                        .frame(width: 10, height: 10)
                                }
                            }
                        }

                        Section("Chats & Groups") {
                            ForEach(vm.chats) { chat in
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(chat.name).font(.headline)
                                    Text(chat.lastMessagePreview)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                    .onAppear { vm.startListeners(uid: uid) }
                    .onDisappear { vm.stopListeners() }
                } else {
                    Text("Sign in with Google or Phone OTP via FirebaseAuth to sync with Android & iPhone TIME users.")
                        .multilineTextAlignment(.center)
                        .padding()
                }
            }
            .navigationTitle("TIME iPhone")
        }
    }
}
