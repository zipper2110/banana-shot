// Sets the main display of a macOS runner to a larger display mode (B-13). The UI flow tests open a 1200 x 800
// window and dialogs up to about 750 pixels high. The default screen of the GitHub macOS runner is 1024 x 768.
// Usage: swift .github/scripts/set-macos-resolution.swift <width> <height>
import CoreGraphics
import Foundation

let arguments = CommandLine.arguments
guard arguments.count == 3, let width = Int(arguments[1]), let height = Int(arguments[2]) else {
    FileHandle.standardError.write("Usage: set-macos-resolution.swift <width> <height>\n".data(using: .utf8)!)
    exit(2)
}

let display = CGMainDisplayID()
let options = [kCGDisplayShowDuplicateLowResolutionModes: kCFBooleanTrue] as CFDictionary
guard let modes = CGDisplayCopyAllDisplayModes(display, options) as? [CGDisplayMode] else {
    FileHandle.standardError.write("The display has no modes.\n".data(using: .utf8)!)
    exit(1)
}
guard let mode = modes.first(where: { $0.width == width && $0.height == height }) else {
    let list = modes.map { "\($0.width)x\($0.height)" }.joined(separator: ", ")
    FileHandle.standardError.write("No mode \(width)x\(height). Modes: \(list)\n".data(using: .utf8)!)
    exit(1)
}

var config: CGDisplayConfigRef?
guard CGBeginDisplayConfiguration(&config) == .success,
      CGConfigureDisplayWithDisplayMode(config, display, mode, nil) == .success,
      CGCompleteDisplayConfiguration(config, .permanently) == .success
else {
    FileHandle.standardError.write("The display mode did not change.\n".data(using: .utf8)!)
    exit(1)
}
print("The main display is now \(CGDisplayPixelsWide(display)) x \(CGDisplayPixelsHigh(display)).")
