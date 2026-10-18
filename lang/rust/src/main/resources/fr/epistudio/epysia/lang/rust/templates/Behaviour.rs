use epysia_sdk::{epysia_component, Behaviour, Input, Transform};

#[epysia_component(category = "Scripts")]
pub struct {{ClassName}} {
    #[export(default = 1.0, label = "Speed")]
    pub speed: f32,
}

impl Behaviour for {{ClassName}} {
    fn on_start(&mut self, _transform: &mut Transform) {
    }

    fn on_update(&mut self, _transform: &mut Transform, _input: &Input, _delta_seconds: f32) {
    }
}
