//! Test-tool state only: counts events received by this window, never key/text contents.
use serde::Serialize;
#[derive(Default, Debug, Serialize)]
pub struct Counts {
    pub mouse_presses: u64,
    pub mouse_releases: u64,
    pub cursor_moves: u64,
    pub drag_moves: u64,
    pub completed_drags: u64,
    pub cancelled_drags: u64,
    pub scroll_events: u64,
    pub key_presses: u64,
    pub key_repeats: u64,
    pub focus_gained: u64,
    pub focus_lost: u64,
    #[serde(skip)]
    held: bool,
    #[serde(skip)]
    dragged: bool,
}
pub enum Event {
    Press,
    Release,
    Motion,
    Scroll,
    Key { repeat: bool },
    Focus(bool),
}
impl Counts {
    pub fn receive(&mut self, event: Event) {
        match event {
            Event::Press => {
                self.mouse_presses += 1;
                self.held = true;
                self.dragged = false;
            }
            Event::Release => {
                self.mouse_releases += 1;
                if self.held && self.dragged {
                    self.completed_drags += 1;
                }
                self.held = false;
                self.dragged = false;
            }
            Event::Motion => {
                self.cursor_moves += 1;
                if self.held {
                    self.drag_moves += 1;
                    self.dragged = true;
                }
            }
            Event::Scroll => self.scroll_events += 1,
            Event::Key { repeat } => {
                if repeat {
                    self.key_repeats += 1;
                } else {
                    self.key_presses += 1;
                }
            }
            Event::Focus(focused) => {
                if focused {
                    self.focus_gained += 1;
                } else {
                    self.focus_lost += 1;
                    if self.held && self.dragged {
                        self.cancelled_drags += 1;
                    }
                    self.held = false;
                    self.dragged = false;
                }
            }
        }
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn drag_counts_require_a_received_press_and_release() {
        let mut s = Counts::default();
        for e in [
            Event::Motion,
            Event::Press,
            Event::Motion,
            Event::Motion,
            Event::Release,
            Event::Motion,
        ] {
            s.receive(e);
        }
        assert_eq!(
            (
                s.mouse_presses,
                s.mouse_releases,
                s.cursor_moves,
                s.drag_moves,
                s.completed_drags
            ),
            (1, 1, 4, 2, 1)
        );
    }
    #[test]
    fn focus_loss_cancels_drag_instead_of_certifying_completion() {
        let mut s = Counts::default();
        for e in [
            Event::Focus(true),
            Event::Press,
            Event::Motion,
            Event::Focus(false),
            Event::Release,
        ] {
            s.receive(e);
        }
        assert_eq!(
            (
                s.focus_gained,
                s.focus_lost,
                s.completed_drags,
                s.cancelled_drags
            ),
            (1, 1, 0, 1)
        );
    }
    #[test]
    fn keyboard_counts_distinguish_repeats_without_recording_text() {
        let mut s = Counts::default();
        for e in [
            Event::Key { repeat: false },
            Event::Key { repeat: true },
            Event::Scroll,
        ] {
            s.receive(e);
        }
        assert_eq!((s.key_presses, s.key_repeats, s.scroll_events), (1, 1, 1));
        let serialized = serde_json::to_string(&s).unwrap();
        assert!(!serialized.contains("held") && !serialized.contains("dragged"));
    }
}
