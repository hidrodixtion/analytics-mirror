#if DEBUG || STAGING    // Only add this plugin in debug or staging builds, not production.
import Foundation
import Hightouch

/// Mirrors every event to a local viewer. Pass-through: never mutates the event.
final class AnalyticsMirrorPlugin: Plugin {
    let type = Hightouch.PluginType.after
    weak var analytics: Hightouch.Analytics?
    
    private let transport: AnalyticsMirrorTransporting
    
    init(transport: AnalyticsMirrorTransporting = AnalyticsMirrorTransport()) {
        self.transport = transport
    }
    
    func execute<T: Hightouch.RawEvent>(event: T?) -> T? {
        guard let event else { return event }
        transport.send(event)
        return event
    }
}

enum AnalyticsMirrorSettings {
    private static let hostKey = "debug.analyticsMirror.host"

    /// "127.0.0.1:9977" on Simulator (shares the Mac's loopback).
    /// On device this must be the Mac's LAN IP.
    static var host: String {
        get { UserDefaults.standard.string(forKey: hostKey) ?? "127.0.0.1:9977" }
        set { UserDefaults.standard.set(newValue, forKey: hostKey) }
    }

    static var endpoint: URL? { URL(string: "http://\(host)/event") }
}

protocol AnalyticsMirrorTransporting {
    func send<T: Hightouch.RawEvent>(_ event: T)
}

final class AnalyticsMirrorTransport: AnalyticsMirrorTransporting {
    private let queue = DispatchQueue(label: "analytics.mirror", qos: .utility)
    private let session: URLSession
    private let encoder: JSONEncoder
    
    init() {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 2
        config.waitsForConnectivity = false
        session = URLSession(configuration: config)
        encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
    }
    
    func send<T: Hightouch.RawEvent>(_ event: T) {
        queue.async { [session, encoder] in
            // resolved per-send so a host change takes effect without an app restart
            guard let endpoint = AnalyticsMirrorSettings.endpoint,
                  let body = try? encoder.encode(event) else { return }
            var request = URLRequest(url: endpoint)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = body
            session.dataTask(with: request) { _, _, error in
                // Log error here
            }.resume()
        }
    }
}
#endif

