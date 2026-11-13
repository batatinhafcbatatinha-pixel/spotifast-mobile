# Make your own theme

A Spotifast Mobile theme is a small JSON file: a name and up to seven
colours. You can write one on your phone, add it in a few seconds, and share
it with anyone as a file or as plain text. Themes change only colours and
type. They run no code, so they cannot slow the app down or see your data.

## The quickest way: start from a theme you like

1. Open **⋮ → Theme** and pick the theme closest to what you want.
2. Under **Make your own**, tap **Copy**. The theme is now on your clipboard
   as JSON.
3. Paste it into any notes or text app, change the `name` and the colours
   you want.
4. Copy the edited text, go back to **⋮ → Theme** and tap **Paste**.

Your theme appears under **Your themes** and is selected straight away.
Importing a theme with the same name again replaces the old one, so you can
keep tweaking and pasting until it looks right.

## The format

```json
{
  "name": "Solarized Dark",
  "dark": true,
  "accent": "#B58900",
  "background": "#002B36",
  "surface": "#073642",
  "raised": "#0E4552",
  "track": "#586E75",
  "muted": "#93A1A1",
  "text": "#FDF6E3",
  "font": "default"
}
```

Only `name` is required. Anything you leave out is taken from Spotifast's
own Dark theme, or its Light theme when `"dark": false`. So this is a
complete theme too:

```json
{ "name": "Dark with orange", "accent": "#FF8800" }
```

| Key | What it colours |
|---|---|
| `name` | The name in the theme list, up to 40 characters |
| `dark` | `true` for a dark theme, `false` for a light one. It also decides whether the status bar icons are light or dark. Default `true` |
| `accent` | Buttons, the play button, the progress bar, selected items |
| `background` | The screen behind everything |
| `surface` | Cards, the mini player, text fields |
| `raised` | Things on top of a surface: pressed rows, menus |
| `track` | The empty part of the progress bar, dividers |
| `muted` | Secondary text: artists, song counts, hints |
| `text` | Main text |
| `font` | `default`, `monospace` or `serif`. Uses the type your phone already has, so it adds nothing to the download |

Colours are `#RRGGBB`, or `#RRGGBBAA` with transparency, the same format as
desktop Spotifast's themes. Text on the accent colour is picked
automatically: dark on a bright accent, white on a deep one.

## Tips for a theme that reads well

- Keep `background`, `surface` and `raised` close together, each a step
  lighter (on a dark theme) or darker (on a light one) than the last.
- Make sure `text` stands out clearly from `background`, and `muted` a bit
  less. A contrast checker such as
  [WebAIM's](https://webaim.org/resources/contrastchecker/) helps: aim for at
  least 4.5:1 for `text` and `muted`.
- Pick an `accent` that is visible on both `background` and `surface`.
- Porting a Spicetify theme? Its `color.ini` maps roughly like this:
  `button` or `button-active` → `accent`, `main` → `background`,
  `sidebar` or `player` → `surface`, `card` → `raised`, `subtext` → `muted`,
  `text` → `text`.

## Adding a theme from a file

Save the JSON as a file ending in `.json` (for example from a download, a
chat or your cloud storage), then tap **⋮ → Theme → Import file** and choose
it. Spotifast reads only the file you pick and asks for no storage
permission.

If something is wrong, the Theme dialog says what, for example
`"accent" must be a colour like #1ED760.`

## Removing a theme

Tap the bin icon next to it under **Your themes**. If it was in use, the app
goes back to following your phone's dark or light mode.

## Share your theme

Made something you like? Share it with others in the
[community themes](themes/) folder: add your `.json` file there in a pull
request, with a screenshot in the pull request description. Anyone can then
open the file on GitHub, tap **Copy raw file**, and paste it into the app.
