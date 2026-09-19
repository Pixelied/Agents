use settings::AppConfig;
#[derive(Default, Debug)]
pub struct Lifecycle {
    pub panic_hidden: bool,
    pub suspended: bool,
    pub settings_open: bool,
    pub quit: bool,
}
impl Lifecycle {
    pub fn hide_all(&mut self) {
        self.panic_hidden = true;
    }
    pub fn show_all(&mut self) {
        self.panic_hidden = false;
    }
    pub fn open_settings(&mut self) {
        self.settings_open = true;
    }
    pub fn close_settings(&mut self) {
        self.settings_open = false;
    }
    pub fn frozen(&self, config: &AppConfig) -> bool {
        self.panic_hidden || self.suspended || self.quit || config.paused || !config.enabled
    }
}
