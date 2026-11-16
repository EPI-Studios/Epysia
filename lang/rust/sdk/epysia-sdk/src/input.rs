use serde_json::Value as JsonValue;

pub struct Input {
    keys_down: Vec<String>,
    keys_pressed: Vec<String>,
    keys_released: Vec<String>,
    mouse_down: Vec<String>,
    mouse_pressed: Vec<String>,
    cursor: (f32, f32),
    mouse_delta: (f32, f32),
    scroll: f32,
}

impl Input {
    pub fn key(&self, name: &str) -> bool {
        contains(&self.keys_down, name)
    }

    pub fn key_pressed(&self, name: &str) -> bool {
        contains(&self.keys_pressed, name)
    }

    pub fn key_released(&self, name: &str) -> bool {
        contains(&self.keys_released, name)
    }

    pub fn mouse(&self, button: &str) -> bool {
        contains(&self.mouse_down, button)
    }

    pub fn mouse_pressed(&self, button: &str) -> bool {
        contains(&self.mouse_pressed, button)
    }

    pub fn cursor(&self) -> (f32, f32) {
        self.cursor
    }

    pub fn mouse_delta(&self) -> (f32, f32) {
        self.mouse_delta
    }

    pub fn scroll(&self) -> f32 {
        self.scroll
    }

    pub(crate) fn from_json(value: &JsonValue) -> Input {
        Input {
            keys_down: strings_of(value, "keysDown"),
            keys_pressed: strings_of(value, "keysPressed"),
            keys_released: strings_of(value, "keysReleased"),
            mouse_down: strings_of(value, "mouseDown"),
            mouse_pressed: strings_of(value, "mousePressed"),
            cursor: pair_of(value, "cursor"),
            mouse_delta: pair_of(value, "mouseDelta"),
            scroll: value
                .get("scroll")
                .and_then(JsonValue::as_f64)
                .unwrap_or(0.0) as f32,
        }
    }
}

fn contains(names: &[String], name: &str) -> bool {
    names
        .iter()
        .any(|candidate| candidate.eq_ignore_ascii_case(name))
}

fn strings_of(value: &JsonValue, key: &str) -> Vec<String> {
    let Some(array) = value.get(key).and_then(JsonValue::as_array) else {
        return Vec::new();
    };
    array
        .iter()
        .filter_map(|entry| entry.as_str().map(str::to_string))
        .collect()
}

fn pair_of(value: &JsonValue, key: &str) -> (f32, f32) {
    let Some(array) = value.get(key).and_then(JsonValue::as_array) else {
        return (0.0, 0.0);
    };
    if array.len() != 2 {
        return (0.0, 0.0);
    }
    (
        array[0].as_f64().unwrap_or(0.0) as f32,
        array[1].as_f64().unwrap_or(0.0) as f32,
    )
}
