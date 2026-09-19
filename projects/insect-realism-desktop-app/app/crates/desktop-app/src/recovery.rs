use std::time::Duration;
/// Numerical retry policy, not a biological parameter. Initial attempt plus two retries.
#[derive(Debug, Default)]
pub struct Recovery {
    pub attempts: u8,
    healthy: bool,
    stable_since: Option<Duration>,
    stable_frames: u32,
    in_flight: bool,
    next: Duration,
    permanent: bool,
}
impl Recovery {
    pub fn begin(&mut self, now: Duration) -> bool {
        if self.healthy || self.in_flight || self.permanent || now < self.next {
            return false;
        }
        self.attempts += 1;
        self.in_flight = true;
        true
    }
    pub fn fail(&mut self, now: Duration) {
        self.stable_since = None;
        self.stable_frames = 0;
        self.healthy = false;
        self.in_flight = false;
        if self.attempts >= 3 {
            self.permanent = true;
            return;
        }
        let delay = if self.attempts <= 1 {
            Duration::from_millis(250)
        } else {
            Duration::from_secs(1)
        };
        self.next = now.saturating_add(delay);
    }
    /// Device construction alone does not replenish the retry budget.
    /// Only sustained presentation health (or explicit user retry) replenishes it.
    pub fn succeed(&mut self) {
        self.stable_since = None;
        self.stable_frames = 0;
        self.healthy = true;
        self.in_flight = false;
        self.permanent = false;
    }
    /// Engineering policy: at least 120 successful presentations over five seconds.
    /// This is recovery hysteresis, not a biological or native performance claim.
    pub fn record_presented(&mut self, now: Duration) {
        if !self.healthy || self.permanent || self.attempts == 0 {
            return;
        }
        let since = *self.stable_since.get_or_insert(now);
        if now < since {
            self.stable_since = Some(now);
            self.stable_frames = 1;
            return;
        }
        self.stable_frames = self.stable_frames.saturating_add(1);
        if self.stable_frames >= 120 && now - since >= Duration::from_secs(5) {
            self.attempts = 0;
            self.stable_since = None;
            self.stable_frames = 0;
        }
    }
    pub fn stop_permanently(&mut self) {
        self.stable_since = None;
        self.stable_frames = 0;
        self.healthy = false;
        self.in_flight = false;
        self.permanent = true;
    }
    pub fn exhausted(&self) -> bool {
        self.permanent
    }
    pub fn ready(&self) -> bool {
        self.healthy
    }
    pub fn deadline(&self) -> Option<Duration> {
        (!self.healthy && !self.permanent && !self.in_flight).then_some(self.next)
    }
    /// Only explicit user retry grants a new attempt budget after permanent failure.
    pub fn retry_by_user(&mut self) {
        *self = Self::default();
    }
}
