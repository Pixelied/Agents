use crate::PlatformError;
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum HotkeyKey {
    Letter(u8),
    Digit(u8),
    Function(u8),
    Escape,
    Space,
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct HotkeyBinding {
    pub control: bool,
    pub alt: bool,
    pub shift: bool,
    pub super_key: bool,
    pub key: HotkeyKey,
}
impl HotkeyBinding {
    pub fn parse(input: &str) -> Result<Self, PlatformError> {
        let fail =
            || PlatformError::Hotkey("use modifiers plus A-Z, 0-9, F1-F12, Space or Escape".into());
        let (mut control, mut alt, mut shift, mut super_key) = (false, false, false, false);
        let mut key = None;
        for part in input.split('+') {
            let token = part.trim().to_ascii_uppercase();
            let modifier = match token.as_str() {
                "CTRL" | "CONTROL" => Some(&mut control),
                "ALT" | "OPTION" => Some(&mut alt),
                "SHIFT" => Some(&mut shift),
                "SUPER" | "WIN" | "CMD" | "COMMAND" => Some(&mut super_key),
                _ => None,
            };
            if let Some(flag) = modifier {
                if *flag {
                    return Err(fail());
                }
                *flag = true;
                continue;
            }
            if key.is_some() {
                return Err(fail());
            }
            key = Some(match token.as_str() {
                "ESC" | "ESCAPE" => HotkeyKey::Escape,
                "SPACE" => HotkeyKey::Space,
                _ if token.len() == 1 && token.as_bytes()[0].is_ascii_uppercase() => {
                    HotkeyKey::Letter(token.as_bytes()[0])
                }
                _ if token.len() == 1 && token.as_bytes()[0].is_ascii_digit() => {
                    HotkeyKey::Digit(token.as_bytes()[0] - b'0')
                }
                _ if token.starts_with('F') => {
                    let n = token[1..].parse::<u8>().map_err(|_| fail())?;
                    if !(1..=12).contains(&n) {
                        return Err(fail());
                    }
                    HotkeyKey::Function(n)
                }
                _ => return Err(fail()),
            });
        }
        if !(control || alt || shift || super_key) {
            return Err(fail());
        }
        Ok(Self {
            control,
            alt,
            shift,
            super_key,
            key: key.ok_or_else(fail)?,
        })
    }
}
