use super::*;
use std::ffi::c_void;
#[repr(C)]
struct EventTypeSpec {
    class: u32,
    kind: u32,
}
#[repr(C)]
#[derive(Clone, Copy)]
struct HotkeyId {
    signature: u32,
    id: u32,
}
type Ref = *mut c_void;
#[link(name = "Carbon", kind = "framework")]
unsafe extern "C" {
    fn GetApplicationEventTarget() -> Ref;
    fn InstallEventHandler(
        target: Ref,
        callback: unsafe extern "C" fn(Ref, Ref, *mut c_void) -> i32,
        count: u32,
        types: *const EventTypeSpec,
        user: *mut c_void,
        out: *mut Ref,
    ) -> i32;
    fn RemoveEventHandler(handler: Ref) -> i32;
    fn RegisterEventHotKey(
        code: u32,
        modifiers: u32,
        id: HotkeyId,
        target: Ref,
        options: u32,
        out: *mut Ref,
    ) -> i32;
    fn UnregisterEventHotKey(key: Ref) -> i32;
    fn GetEventParameter(
        event: Ref,
        name: u32,
        kind: u32,
        actual: *mut u32,
        size: std::ffi::c_ulong,
        actual_size: *mut std::ffi::c_ulong,
        data: *mut c_void,
    ) -> i32;
}
struct CallbackState {
    events: Events,
    current: std::cell::Cell<u32>,
}
unsafe extern "C" fn callback(_call: Ref, event: Ref, user: *mut c_void) -> i32 {
    if user.is_null() {
        return -9874;
    }
    let mut id = HotkeyId {
        signature: 0,
        id: 0,
    };
    // SAFETY: Carbon invokes this handler on the registered main-thread event target; boxed userdata lives until handler removal.
    let status = unsafe {
        GetEventParameter(
            event,
            u32::from_be_bytes(*b"----"),
            u32::from_be_bytes(*b"hkid"),
            std::ptr::null_mut(),
            std::mem::size_of::<HotkeyId>() as std::ffi::c_ulong,
            std::ptr::null_mut(),
            (&mut id as *mut HotkeyId).cast(),
        )
    };
    let state = unsafe { &*user.cast::<CallbackState>() };
    if status == 0 && id.signature == u32::from_be_bytes(*b"INSC") && id.id == state.current.get() {
        emit(&state.events, PlatformEvent::PanicHotkey);
        return 0;
    }
    -9874
}
pub(super) struct Hotkey {
    handler: Ref,
    key: Ref,
    binding: Option<HotkeyBinding>,
    state: Box<CallbackState>,
    generation: u32,
}
impl Hotkey {
    pub fn new(events: Events) -> Result<Self, PlatformError> {
        let mut state = Box::new(CallbackState {
            events,
            current: std::cell::Cell::new(0),
        });
        let mut handler = std::ptr::null_mut();
        let event = EventTypeSpec {
            class: u32::from_be_bytes(*b"keyb"),
            kind: crate::CARBON_HOTKEY_PRESSED,
        };
        let status = unsafe {
            InstallEventHandler(
                GetApplicationEventTarget(),
                callback,
                1,
                &event,
                (&mut *state as *mut CallbackState).cast(),
                &mut handler,
            )
        };
        if status != 0 {
            return Err(PlatformError::Hotkey(format!(
                "Carbon event handler returned {status}"
            )));
        }
        Ok(Self {
            handler,
            key: std::ptr::null_mut(),
            binding: None,
            state,
            generation: 0,
        })
    }
    pub fn register(&mut self, text: &str) -> Result<(), PlatformError> {
        let binding = HotkeyBinding::parse(text)?;
        if self.binding == Some(binding) {
            return Ok(());
        }
        let code = crate::carbon_key(binding.key)
            .ok_or(PlatformError::Hotkey("unsupported physical key".into()))?;
        let modifiers = (u32::from(binding.super_key) << 8)
            | (u32::from(binding.shift) << 9)
            | (u32::from(binding.alt) << 11)
            | (u32::from(binding.control) << 12);
        let id = self.generation.wrapping_add(1).max(1);
        let mut replacement = std::ptr::null_mut();
        let status = unsafe {
            RegisterEventHotKey(
                code,
                modifiers,
                HotkeyId {
                    signature: u32::from_be_bytes(*b"INSC"),
                    id,
                },
                GetApplicationEventTarget(),
                0,
                &mut replacement,
            )
        };
        if status != 0 {
            return Err(PlatformError::Hotkey(format!(
                "shortcut is unavailable (Carbon {status}); previous binding retained"
            )));
        }
        if !self.key.is_null() {
            unsafe { UnregisterEventHotKey(self.key) };
        }
        self.key = replacement;
        self.binding = Some(binding);
        self.generation = id;
        self.state.current.set(id);
        Ok(())
    }
}
impl Drop for Hotkey {
    fn drop(&mut self) {
        unsafe {
            if !self.key.is_null() {
                UnregisterEventHotKey(self.key);
            }
            if !self.handler.is_null() {
                RemoveEventHandler(self.handler);
            }
        }
    }
}

#[cfg(test)]
mod abi_tests {
    use super::*;
    #[test]
    fn event_parameter_uses_native_unsigned_long_byte_count() {
        // Apple's MacTypes.h defines ByteCount as unsigned long, not UInt32.
        let _signature: unsafe extern "C" fn(
            Ref,
            u32,
            u32,
            *mut u32,
            std::ffi::c_ulong,
            *mut std::ffi::c_ulong,
            *mut c_void,
        ) -> i32 = GetEventParameter;
    }
}
