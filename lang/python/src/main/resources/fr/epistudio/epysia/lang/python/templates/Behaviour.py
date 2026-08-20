from epysia import Behaviour, component, export


@component(name="{{ClassName}}", category="Scripts")
class {{ClassName}}(Behaviour):

    speed = export(1.0, label="Speed")

    def on_start(self):
        pass

    def on_update(self, input, delta_seconds):
        pass
