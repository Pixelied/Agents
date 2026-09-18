use std::time::Duration;
/// Integer rational accumulator: one tick = 1e9 accumulator units, regardless
/// of refresh rate. A frame stall never causes minutes of catch-up work.
#[derive(Debug, Clone)]
pub struct FixedClock {
    hz: u32,
    remainder: u128,
    pub tick: u64,
    pub dropped: u64,
    paused: bool,
}
#[derive(Clone, Copy, Debug, Default)]
pub struct ClockAdvance {
    pub ticks: u32,
    pub alpha: f32,
    pub dropped_ticks: u64,
}
impl FixedClock {
    pub fn new(hz: u32) -> Option<Self> {
        (1..=240).contains(&hz).then_some(Self {
            hz,
            remainder: 0,
            tick: 0,
            dropped: 0,
            paused: false,
        })
    }
    pub fn dt(&self) -> f32 {
        1.0 / self.hz as f32
    }
    pub fn paused(&self) -> bool {
        self.paused
    }
    pub fn set_paused(&mut self, paused: bool) {
        if self.paused != paused {
            self.remainder = 0;
        }
        self.paused = paused;
    }
    pub fn advance(&mut self, dt: Duration) -> ClockAdvance {
        if self.paused {
            return ClockAdvance::default();
        }
        let units = dt
            .as_nanos()
            .saturating_mul(self.hz.into())
            .saturating_add(self.remainder);
        let pending = units / 1_000_000_000;
        let ticks = pending.min(8) as u32;
        let dropped = (pending - u128::from(ticks)).min(u128::from(u64::MAX)) as u64;
        self.remainder = units % 1_000_000_000;
        self.tick = self.tick.saturating_add(ticks.into());
        self.dropped = self.dropped.saturating_add(dropped);
        ClockAdvance {
            ticks,
            alpha: self.remainder as f32 / 1_000_000_000.0,
            dropped_ticks: dropped,
        }
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn integer_partition_is_refresh_independent() {
        for hz in [60, 120, 144, 165, 240] {
            let mut c = FixedClock::new(30).unwrap();
            let mut ns = 0;
            for i in 1..=hz {
                let next = 1_000_000_000u64 * i / hz;
                c.advance(Duration::from_nanos(next - ns));
                ns = next;
            }
            assert_eq!(c.tick, 30);
        }
    }
    #[test]
    fn pause_clears_partial_time() {
        let mut c = FixedClock::new(30).unwrap();
        c.advance(Duration::from_millis(20));
        c.set_paused(true);
        c.advance(Duration::from_secs(3600));
        c.set_paused(false);
        assert_eq!(c.advance(Duration::from_millis(20)).ticks, 0);
    }
}
