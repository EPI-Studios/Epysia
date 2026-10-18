use std::cell::RefCell;
use std::collections::BTreeMap;

pub use epysia_macros::epysia_component;
pub use serde_json::Value as JsonValue;

mod input;
mod transform;
mod vec3;

pub mod abi;

pub use input::Input;
pub use transform::Transform;
pub use vec3::Vec3;

pub trait Behaviour: Default {
    fn on_start(&mut self, transform: &mut Transform) {
        let _ = transform;
    }
    fn on_update(&mut self, transform: &mut Transform, input: &Input, delta_seconds: f32) {
        let _ = (transform, input, delta_seconds);
    }
    fn on_fixed_update(&mut self, transform: &mut Transform, fixed_step_seconds: f32) {
        let _ = (transform, fixed_step_seconds);
    }
    fn on_destroy(&mut self) {}
}

pub trait Component: Behaviour + 'static {
    fn descriptor() -> ComponentDescriptor;
    fn get_json(&self, name: &str) -> Option<JsonValue>;
    fn set_json(&mut self, name: &str, value: &JsonValue);
}

#[derive(serde::Serialize)]
pub struct PropertyDescriptor {
    pub name: &'static str,
    pub label: &'static str,
    #[serde(rename = "type")]
    pub kind: &'static str,
    pub default: JsonValue,
    pub min: f32,
    pub max: f32,
    pub step: f32,
    pub color: bool,
}

#[derive(serde::Serialize)]
pub struct ComponentDescriptor {
    pub name: &'static str,
    pub category: &'static str,
    pub description: &'static str,
    pub properties: Vec<PropertyDescriptor>,
}

pub trait AnyComponent {
    fn on_start(&mut self, transform: &mut Transform);
    fn on_update(&mut self, transform: &mut Transform, input: &Input, delta_seconds: f32);
    fn on_fixed_update(&mut self, transform: &mut Transform, fixed_step_seconds: f32);
    fn on_destroy(&mut self);
    fn get(&self, name: &str) -> Option<JsonValue>;
    fn set(&mut self, name: &str, value: &JsonValue);
}

impl<T: Component> AnyComponent for T {
    fn on_start(&mut self, transform: &mut Transform) {
        Behaviour::on_start(self, transform);
    }

    fn on_update(&mut self, transform: &mut Transform, input: &Input, delta_seconds: f32) {
        Behaviour::on_update(self, transform, input, delta_seconds);
    }

    fn on_fixed_update(&mut self, transform: &mut Transform, fixed_step_seconds: f32) {
        Behaviour::on_fixed_update(self, transform, fixed_step_seconds);
    }

    fn on_destroy(&mut self) {
        Behaviour::on_destroy(self);
    }

    fn get(&self, name: &str) -> Option<JsonValue> {
        Component::get_json(self, name)
    }

    fn set(&mut self, name: &str, value: &JsonValue) {
        Component::set_json(self, name, value);
    }
}

pub type Factory = fn() -> Box<dyn AnyComponent>;

#[derive(Default)]
pub struct Registry {
    // not a HashMap, its random seed panics on wasm32-unknown-unknown
    instances: BTreeMap<i32, Box<dyn AnyComponent>>,
    next_handle: i32,
}

impl Registry {
    fn insert(&mut self, instance: Box<dyn AnyComponent>) -> i32 {
        self.next_handle += 1;
        let handle = self.next_handle;
        self.instances.insert(handle, instance);
        handle
    }

    fn get(&self, handle: i32) -> Option<&dyn AnyComponent> {
        self.instances.get(&handle).map(|boxed| boxed.as_ref())
    }

    fn get_mut(&mut self, handle: i32) -> Option<&mut Box<dyn AnyComponent>> {
        self.instances.get_mut(&handle)
    }

    fn remove(&mut self, handle: i32) -> Option<Box<dyn AnyComponent>> {
        self.instances.remove(&handle)
    }
}

thread_local! {
    pub static REGISTRY: RefCell<Registry> = RefCell::new(Registry::default());
}

#[macro_export]
macro_rules! epysia_crate {
    ( $( $component:path ),* $(,)? ) => {
        #[unsafe(no_mangle)]
        pub extern "C" fn epysia_alloc(len: i32) -> i32 {
            $crate::abi::alloc(len)
        }

        #[unsafe(no_mangle)]
        pub extern "C" fn epysia_dealloc(ptr: i32, len: i32) {
            $crate::abi::dealloc(ptr, len)
        }

        #[unsafe(no_mangle)]
        pub extern "C" fn epysia_manifest() -> i64 {
            let descriptors: ::std::vec::Vec<$crate::ComponentDescriptor> =
                ::std::vec![ $( <$component as $crate::Component>::descriptor() ),* ];
            $crate::abi::manifest(&descriptors)
        }

        #[unsafe(no_mangle)]
        pub extern "C" fn epysia_new(index: i32) -> i32 {
            let factories: &[$crate::Factory] = &[
                $( (|| ::std::boxed::Box::new(<$component as ::std::default::Default>::default())
                    as ::std::boxed::Box<dyn $crate::AnyComponent>) ),*
            ];
            $crate::abi::new_instance(factories, index)
        }

        #[unsafe(no_mangle)]
        pub extern "C" fn epysia_read(handle: i32, name_ptr: i32, name_len: i32) -> i64 {
            $crate::abi::read(handle, name_ptr, name_len)
        }

        #[unsafe(no_mangle)]
        pub extern "C" fn epysia_write(handle: i32, name_ptr: i32, name_len: i32, value_ptr: i32, value_len: i32) {
            $crate::abi::write(handle, name_ptr, name_len, value_ptr, value_len)
        }

        #[unsafe(no_mangle)]
        pub extern "C" fn epysia_on_start(handle: i32, transform_ptr: i32, transform_len: i32) -> i64 {
            $crate::abi::on_start(handle, transform_ptr, transform_len)
        }

        #[unsafe(no_mangle)]
        pub extern "C" fn epysia_on_update(handle: i32, delta_seconds: f32, transform_ptr: i32, transform_len: i32,
                input_ptr: i32, input_len: i32) -> i64 {
            $crate::abi::on_update(handle, delta_seconds, transform_ptr, transform_len, input_ptr, input_len)
        }

        #[unsafe(no_mangle)]
        pub extern "C" fn epysia_on_fixed_update(handle: i32, fixed_step_seconds: f32, transform_ptr: i32,
                transform_len: i32) -> i64 {
            $crate::abi::on_fixed_update(handle, fixed_step_seconds, transform_ptr, transform_len)
        }

        #[unsafe(no_mangle)]
        pub extern "C" fn epysia_on_destroy(handle: i32) {
            $crate::abi::on_destroy(handle)
        }
    };
}
