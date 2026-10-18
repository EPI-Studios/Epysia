use crate::{AnyComponent, ComponentDescriptor, Factory, Input, JsonValue, Transform, REGISTRY};

pub fn alloc(len: i32) -> i32 {
    let mut buffer = vec![0u8; len.max(0) as usize];
    let ptr = buffer.as_mut_ptr();
    std::mem::forget(buffer);
    ptr as i32
}

pub fn dealloc(ptr: i32, len: i32) {
    if ptr == 0 {
        return;
    }
    unsafe {
        drop(Vec::from_raw_parts(
            ptr as *mut u8,
            len.max(0) as usize,
            len.max(0) as usize,
        ));
    }
}

fn pack(ptr: i32, len: i32) -> i64 {
    ((ptr as u32 as i64) << 32) | (len as u32 as i64)
}

fn leaked(bytes: Vec<u8>) -> i64 {
    // capacity must equal len, dealloc rebuilds the Vec from (ptr, len) and from_raw_parts is UB otherwise
    let boxed = bytes.into_boxed_slice();
    let len = boxed.len() as i32;
    let ptr = boxed.as_ptr() as i32;
    std::mem::forget(boxed);
    pack(ptr, len)
}

unsafe fn str_at(ptr: i32, len: i32) -> String {
    let bytes = unsafe { std::slice::from_raw_parts(ptr as *const u8, len.max(0) as usize) };
    String::from_utf8_lossy(bytes).into_owned()
}

unsafe fn json_at(ptr: i32, len: i32) -> JsonValue {
    let bytes = unsafe { std::slice::from_raw_parts(ptr as *const u8, len.max(0) as usize) };
    serde_json::from_slice(bytes).unwrap_or(JsonValue::Null)
}

pub fn manifest(descriptors: &[ComponentDescriptor]) -> i64 {
    let json = serde_json::to_vec(descriptors).unwrap_or_else(|_| b"[]".to_vec());
    leaked(json)
}

pub fn new_instance(factories: &[Factory], index: i32) -> i32 {
    let Some(factory) = usize::try_from(index)
        .ok()
        .and_then(|index| factories.get(index))
    else {
        return 0;
    };
    let instance: Box<dyn AnyComponent> = factory();
    REGISTRY.with(|registry| registry.borrow_mut().insert(instance))
}

pub fn read(handle: i32, name_ptr: i32, name_len: i32) -> i64 {
    let name = unsafe { str_at(name_ptr, name_len) };
    let value = REGISTRY.with(|registry| {
        registry
            .borrow()
            .get(handle)
            .and_then(|instance| instance.get(&name))
    });
    let json: JsonValue = value.unwrap_or(JsonValue::Null);
    leaked(serde_json::to_vec(&json).unwrap_or_else(|_| b"null".to_vec()))
}

pub fn write(handle: i32, name_ptr: i32, name_len: i32, value_ptr: i32, value_len: i32) {
    let name = unsafe { str_at(name_ptr, name_len) };
    let raw =
        unsafe { std::slice::from_raw_parts(value_ptr as *const u8, value_len.max(0) as usize) };
    let Ok(value) = serde_json::from_slice::<JsonValue>(raw) else {
        return;
    };
    REGISTRY.with(|registry| {
        if let Some(instance) = registry.borrow_mut().get_mut(handle) {
            instance.set(&name, &value);
        }
    });
}

pub fn on_start(handle: i32, transform_ptr: i32, transform_len: i32) -> i64 {
    let mut transform = Transform::from_json(&unsafe { json_at(transform_ptr, transform_len) });
    REGISTRY.with(|registry| {
        if let Some(instance) = registry.borrow_mut().get_mut(handle) {
            instance.on_start(&mut transform);
        }
    });
    leaked(serde_json::to_vec(&transform.to_json()).unwrap_or_else(|_| b"{}".to_vec()))
}

pub fn on_update(
    handle: i32,
    delta_seconds: f32,
    transform_ptr: i32,
    transform_len: i32,
    input_ptr: i32,
    input_len: i32,
) -> i64 {
    let mut transform = Transform::from_json(&unsafe { json_at(transform_ptr, transform_len) });
    let input = Input::from_json(&unsafe { json_at(input_ptr, input_len) });
    REGISTRY.with(|registry| {
        if let Some(instance) = registry.borrow_mut().get_mut(handle) {
            instance.on_update(&mut transform, &input, delta_seconds);
        }
    });
    leaked(serde_json::to_vec(&transform.to_json()).unwrap_or_else(|_| b"{}".to_vec()))
}

pub fn on_fixed_update(
    handle: i32,
    fixed_step_seconds: f32,
    transform_ptr: i32,
    transform_len: i32,
) -> i64 {
    let mut transform = Transform::from_json(&unsafe { json_at(transform_ptr, transform_len) });
    REGISTRY.with(|registry| {
        if let Some(instance) = registry.borrow_mut().get_mut(handle) {
            instance.on_fixed_update(&mut transform, fixed_step_seconds);
        }
    });
    leaked(serde_json::to_vec(&transform.to_json()).unwrap_or_else(|_| b"{}".to_vec()))
}

pub fn on_destroy(handle: i32) {
    REGISTRY.with(|registry| {
        if let Some(mut instance) = registry.borrow_mut().remove(handle) {
            instance.on_destroy();
        }
    });
}
