use crate::Vec3;
use serde_json::Value as JsonValue;

pub struct Transform {
    pub position: Vec3,
    pub scale: Vec3,
    world_position: Vec3,
}

impl Transform {
    pub fn world_position(&self) -> Vec3 {
        self.world_position
    }

    pub fn translate(&mut self, delta: Vec3) {
        self.position += delta;
    }

    pub(crate) fn from_json(value: &JsonValue) -> Transform {
        Transform {
            position: vec3_of(value, "position", Vec3::ZERO),
            scale: vec3_of(value, "scale", Vec3::ONE),
            world_position: vec3_of(value, "worldPosition", Vec3::ZERO),
        }
    }

    pub(crate) fn to_json(&self) -> JsonValue {
        serde_json::json!({
            "position": [self.position.x, self.position.y, self.position.z],
            "scale": [self.scale.x, self.scale.y, self.scale.z],
        })
    }
}

fn vec3_of(value: &JsonValue, key: &str, fallback: Vec3) -> Vec3 {
    let Some(array) = value.get(key).and_then(JsonValue::as_array) else {
        return fallback;
    };
    if array.len() != 3 {
        return fallback;
    }
    Vec3::new(
        array[0].as_f64().unwrap_or(0.0) as f32,
        array[1].as_f64().unwrap_or(0.0) as f32,
        array[2].as_f64().unwrap_or(0.0) as f32,
    )
}
