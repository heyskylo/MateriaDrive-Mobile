import SwiftUI
import ComposeApp

// Hosts the Compose Multiplatform UI (Google's Material 3 Expressive, rendered by Compose).
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController { MainViewControllerKt.MainViewController() }
    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup { ComposeView().ignoresSafeArea(.all) }
    }
}
