import SwiftUI
import UIKit

/// The colours the screens use.
struct Palette: Equatable {
    var accent: Color
    var background: Color
    var surface: Color
    var raised: Color
    var track: Color
    var muted: Color
    var text: Color
    var dark: Bool
    var design: Font.Design = .default

    /// Text on top of the accent colour.
    var onAccent: Color { accentIsLight ? Color(hex: 0x0F1114) : .white }
    var accentIsLight = true
}

extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}

private func palette(
    _ a: UInt32, _ bg: UInt32, _ s: UInt32, _ r: UInt32, _ t: UInt32, _ m: UInt32, _ x: UInt32,
    dark: Bool = true, design: Font.Design = .default, lightAccent: Bool = true
) -> Palette {
    Palette(
        accent: Color(hex: a), background: Color(hex: bg), surface: Color(hex: s), raised: Color(hex: r),
        track: Color(hex: t), muted: Color(hex: m), text: Color(hex: x), dark: dark, design: design,
        accentIsLight: lightAccent
    )
}

private let darkBase = palette(0x1ED760, 0x0F1114, 0x181B20, 0x2A2F36, 0x4A5059, 0xA5ADBA, 0xE8EAED)
private let lightBase = palette(0x128A3E, 0xF7F8FA, 0xECEEF2, 0xE1E4EA, 0xC5CAD3, 0x5F6670, 0x15181C, dark: false, lightAccent: false)
private let blackBase = palette(0x1ED760, 0x000000, 0x0E0E0E, 0x1A1A1A, 0x3A3A3A, 0x9AA0A8, 0xE8EAED)

/// Themes. The first four take an accent colour; the rest bring their own,
/// after well-known Spicetify themes and palettes.
enum Appearance: String, CaseIterable, Identifiable {
    case system, dark, light, black, dynamic
    case mocha, latte, dracula, nord, gruvbox, fluent, comfy, throwback
    case eighties, nineties, terminal, cyberpunk, coffee, rose

    var id: String { rawValue }

    var label: String {
        switch self {
        case .system: "Same as the phone"
        case .dark: "Dark"
        case .light: "Light"
        case .black: "Black (AMOLED)"
        case .dynamic: "Dynamic (from the cover)"
        case .mocha: "Catppuccin Mocha"
        case .latte: "Catppuccin Latte"
        case .dracula: "Dracula"
        case .nord: "Nord"
        case .gruvbox: "Gruvbox"
        case .fluent: "Fluent"
        case .comfy: "Comfy"
        case .throwback: "Throwback"
        case .eighties: "80s synthwave"
        case .nineties: "90s vaporwave"
        case .terminal: "Terminal"
        case .cyberpunk: "Cyberpunk"
        case .coffee: "Coffee"
        case .rose: "Rose"
        }
    }

    var takesAccent: Bool { [.system, .dark, .light, .black].contains(self) }

    fileprivate var preset: Palette? {
        switch self {
        case .mocha: palette(0xCBA6F7, 0x1E1E2E, 0x181825, 0x313244, 0x45475A, 0xA6ADC8, 0xCDD6F4)
        case .latte: palette(0x8839EF, 0xEFF1F5, 0xE6E9EF, 0xCCD0DA, 0xBCC0CC, 0x6C6F85, 0x4C4F69, dark: false, lightAccent: false)
        case .dracula: palette(0xBD93F9, 0x282A36, 0x21222C, 0x44475A, 0x6272A4, 0x9EA8C7, 0xF8F8F2)
        case .nord: palette(0x88C0D0, 0x2E3440, 0x3B4252, 0x434C5E, 0x4C566A, 0xA3B1C6, 0xECEFF4)
        case .gruvbox: palette(0xFE8019, 0x282828, 0x32302F, 0x3C3836, 0x504945, 0xA89984, 0xEBDBB2)
        case .fluent: palette(0x60CDFF, 0x202020, 0x2B2B2B, 0x353535, 0x4A4A4A, 0xC5C5C5, 0xFFFFFF)
        case .comfy: palette(0x8FA4F5, 0x16191E, 0x1E2229, 0x282D36, 0x3A4150, 0x8D96A6, 0xDCE0E8)
        case .throwback: palette(0x84BD00, 0x121314, 0x222326, 0x2E2F33, 0x45464B, 0xA0A0A0, 0xFFFFFF)
        case .eighties: palette(0xFF4FD8, 0x1A0B2E, 0x261447, 0x341D5C, 0x00E5FF, 0x9D8FD0, 0xF6E8FF, lightAccent: false)
        case .nineties: palette(0xFF71CE, 0x2D1B4E, 0x3B2766, 0x4A3380, 0x01CDFE, 0xB9F2E4, 0xFFFBF0, design: .monospaced)
        case .terminal: palette(0x33FF66, 0x020A04, 0x07160B, 0x0D2414, 0x1C5A2E, 0x29B34F, 0xB8FFC8, design: .monospaced)
        case .cyberpunk: palette(0xFCEE0A, 0x050608, 0x101318, 0x1A1F27, 0x00F0FF, 0x00C2CC, 0xEAF6FF, design: .monospaced)
        case .coffee: palette(0xD4A373, 0x1E1410, 0x2C1E17, 0x3B2A21, 0x6B4F3F, 0xC9B29B, 0xF3E9DC, design: .serif)
        case .rose: palette(0xD63384, 0xFFF0F6, 0xFCE1EC, 0xF8CCDD, 0xF1A7C5, 0x8C5A70, 0x3A1027, dark: false, lightAccent: false)
        default: nil
        }
    }
}

enum Accent: String, CaseIterable, Identifiable {
    case green, blue, purple, pink, orange, red
    var id: String { rawValue }
    var label: String { rawValue.capitalized }
    var dark: Color {
        switch self {
        case .green: Color(hex: 0x1ED760)
        case .blue: Color(hex: 0x4EA8FF)
        case .purple: Color(hex: 0xB98CFF)
        case .pink: Color(hex: 0xFF7AB6)
        case .orange: Color(hex: 0xFFA24C)
        case .red: Color(hex: 0xFF6B6B)
        }
    }
    var light: Color {
        switch self {
        case .green: Color(hex: 0x128A3E)
        case .blue: Color(hex: 0x1765C1)
        case .purple: Color(hex: 0x6B3FC4)
        case .pink: Color(hex: 0xC2185B)
        case .orange: Color(hex: 0xC25E00)
        case .red: Color(hex: 0xC62828)
        }
    }
}

/// Colours picked from a cover: its most vivid colour and its average one.
struct CoverColors: Equatable {
    var vivid: UIColor
    var average: UIColor
}

private func mix(_ top: UIColor, _ amount: CGFloat, over bottom: UIColor) -> UIColor {
    var (r1, g1, b1, a1): (CGFloat, CGFloat, CGFloat, CGFloat) = (0, 0, 0, 0)
    var (r2, g2, b2, a2): (CGFloat, CGFloat, CGFloat, CGFloat) = (0, 0, 0, 0)
    top.getRed(&r1, green: &g1, blue: &b1, alpha: &a1)
    bottom.getRed(&r2, green: &g2, blue: &b2, alpha: &a2)
    return UIColor(red: r1 * amount + r2 * (1 - amount), green: g1 * amount + g2 * (1 - amount),
                   blue: b1 * amount + b2 * (1 - amount), alpha: 1)
}

private func luminance(_ c: UIColor) -> CGFloat {
    var (r, g, b, a): (CGFloat, CGFloat, CGFloat, CGFloat) = (0, 0, 0, 0)
    c.getRed(&r, green: &g, blue: &b, alpha: &a)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

private func dynamicPalette(_ cover: CoverColors?) -> Palette {
    guard let cover else { return darkBase }
    // A deep shade of the cover as the background, its vivid colour as the
    // accent, lightened until it reads on that shade.
    let base = UIColor(red: 0.043, green: 0.047, blue: 0.055, alpha: 1)
    let background = mix(cover.average, 0.22, over: base)
    var accent = cover.vivid
    var steps = 0
    while luminance(accent) < 0.3 && steps < 8 {
        accent = mix(.white, 0.25, over: accent)
        steps += 1
    }
    var p = darkBase
    p.accent = Color(accent)
    p.accentIsLight = luminance(accent) > 0.4
    p.background = Color(background)
    p.surface = Color(mix(.white, 0.05, over: background))
    p.raised = Color(mix(.white, 0.10, over: background))
    p.track = Color(mix(.white, 0.22, over: background))
    return p
}

func makePalette(_ appearance: Appearance, accent: Accent, systemDark: Bool, cover: CoverColors?) -> Palette {
    switch appearance {
    case .system:
        var p = systemDark ? darkBase : lightBase
        p.accent = systemDark ? accent.dark : accent.light
        return p
    case .dark:
        var p = darkBase
        p.accent = accent.dark
        return p
    case .black:
        var p = blackBase
        p.accent = accent.dark
        return p
    case .light:
        var p = lightBase
        p.accent = accent.light
        return p
    case .dynamic:
        return dynamicPalette(cover)
    default:
        return appearance.preset ?? darkBase
    }
}

private struct PaletteKey: EnvironmentKey {
    static let defaultValue = darkBase
}

extension EnvironmentValues {
    var palette: Palette {
        get { self[PaletteKey.self] }
        set { self[PaletteKey.self] = newValue }
    }
}

/// The vivid and average colours of an image, from a small copy of it.
func coverColors(of image: UIImage) -> CoverColors? {
    let side = 24
    guard let cg = image.cgImage else { return nil }
    var pixels = [UInt8](repeating: 0, count: side * side * 4)
    let drawn = pixels.withUnsafeMutableBytes { buffer -> Bool in
        guard let context = CGContext(
            data: buffer.baseAddress, width: side, height: side, bitsPerComponent: 8, bytesPerRow: side * 4,
            space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else { return false }
        context.draw(cg, in: CGRect(x: 0, y: 0, width: side, height: side))
        return true
    }
    guard drawn else { return nil }
    var sum = (0.0, 0.0, 0.0)
    var best: UIColor = .gray
    var bestScore: CGFloat = -1
    for i in stride(from: 0, to: pixels.count, by: 4) {
        let r = CGFloat(pixels[i]) / 255, g = CGFloat(pixels[i + 1]) / 255, b = CGFloat(pixels[i + 2]) / 255
        sum.0 += Double(r); sum.1 += Double(g); sum.2 += Double(b)
        let color = UIColor(red: r, green: g, blue: b, alpha: 1)
        var h: CGFloat = 0, s: CGFloat = 0, v: CGFloat = 0, a: CGFloat = 0
        color.getHue(&h, saturation: &s, brightness: &v, alpha: &a)
        // Vivid: saturated and neither too dark nor washed out.
        let score = s * (1 - abs(v - 0.75))
        if score > bestScore {
            bestScore = score
            best = color
        }
    }
    let n = Double(side * side)
    let average = UIColor(red: sum.0 / n, green: sum.1 / n, blue: sum.2 / n, alpha: 1)
    return CoverColors(vivid: best, average: average)
}
