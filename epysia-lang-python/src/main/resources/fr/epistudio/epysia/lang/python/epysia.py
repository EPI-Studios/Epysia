import java
import math

_Vector3f = java.type("org.joml.Vector3f")
_KeyCode = java.type("fr.epistudio.epysia.input.KeyCode")
_MouseButton = java.type("fr.epistudio.epysia.input.MouseButton")
_host = java.type("fr.epistudio.epysia.lang.python.PythonHost")
_f = _host.toFloat

_registered = []


class Vec3:

    __slots__ = ("x", "y", "z")

    def __init__(self, x=0.0, y=0.0, z=0.0):
        self.x = float(x)
        self.y = float(y)
        self.z = float(z)

    @staticmethod
    def of(native):
        return Vec3(native.x, native.y, native.z)

    def to_java(self):
        return _Vector3f(_f(self.x), _f(self.y), _f(self.z))

    def __add__(self, other):
        return Vec3(self.x + other.x, self.y + other.y, self.z + other.z)

    def __sub__(self, other):
        return Vec3(self.x - other.x, self.y - other.y, self.z - other.z)

    def __mul__(self, factor):
        return Vec3(self.x * factor, self.y * factor, self.z * factor)

    __rmul__ = __mul__

    def __truediv__(self, divisor):
        return Vec3(self.x / divisor, self.y / divisor, self.z / divisor)

    def __neg__(self):
        return Vec3(-self.x, -self.y, -self.z)

    def __eq__(self, other):
        return isinstance(other, Vec3) and (self.x, self.y, self.z) == (other.x, other.y, other.z)

    def __iter__(self):
        return iter((self.x, self.y, self.z))

    def __repr__(self):
        return "Vec3(%g, %g, %g)" % (self.x, self.y, self.z)

    def length(self):
        return math.sqrt(self.x * self.x + self.y * self.y + self.z * self.z)

    def normalized(self):
        size = self.length()
        return Vec3() if size == 0.0 else self / size

    def dot(self, other):
        return self.x * other.x + self.y * other.y + self.z * other.z

    def cross(self, other):
        return Vec3(self.y * other.z - self.z * other.y,
                    self.z * other.x - self.x * other.z,
                    self.x * other.y - self.y * other.x)


ZERO = Vec3()
UP = Vec3(0.0, 1.0, 0.0)
RIGHT = Vec3(1.0, 0.0, 0.0)
FORWARD = Vec3(0.0, 0.0, -1.0)


class Transform:

    __slots__ = ("_native",)

    def __init__(self, native):
        self._native = native

    @property
    def position(self):
        return Vec3.of(self._native.position())

    @position.setter
    def position(self, value):
        self._native.setPosition(_f(value.x), _f(value.y), _f(value.z))

    @property
    def world_position(self):
        return Vec3.of(self._native.worldPosition(_Vector3f()))

    @world_position.setter
    def world_position(self, value):
        self._native.setWorldPosition(_f(value.x), _f(value.y), _f(value.z))

    @property
    def scale(self):
        return Vec3.of(self._native.scale())

    @scale.setter
    def scale(self, value):
        self._native.setScale(_f(value.x), _f(value.y), _f(value.z))

    @property
    def visible(self):
        return self._native.visible()

    @visible.setter
    def visible(self, value):
        self._native.setVisible(bool(value))

    def move(self, delta):
        self._native.translate(_f(delta.x), _f(delta.y), _f(delta.z))

    def rotate(self, pitch=0.0, yaw=0.0, roll=0.0):
        self._native.setRotationEuler(_f(math.radians(pitch)), _f(math.radians(yaw)),
                                      _f(math.radians(roll)))

    def spin(self, axis, degrees):
        self._native.rotateAxisAngle(_f(axis.x), _f(axis.y), _f(axis.z), _f(math.radians(degrees)))

    def look_at(self, target, up=UP):
        self._native.lookAt(_f(target.x), _f(target.y), _f(target.z), _f(up.x), _f(up.y), _f(up.z))


class Object3D:

    __slots__ = ("_native",)

    def __init__(self, native):
        self._native = native

    @property
    def name(self):
        return self._native.name()

    @property
    def tag(self):
        return self._native.tag()

    @tag.setter
    def tag(self, value):
        self._native.setTag(value)

    @property
    def alive(self):
        return self._native.isAlive()

    @property
    def transform(self):
        return Transform(self._native.transform3DOrNull())

    @property
    def parent(self):
        found = self._native.parentOrNull()
        return None if found is None else Object3D(found)

    @property
    def children(self):
        return [Object3D(child) for child in self._native.children()]

    def child(self, name):
        for child in self._native.children():
            if child.name() == name:
                return Object3D(child)
        return None

    def destroy(self):
        self._native.markDestroyed()

    def __repr__(self):
        return "Object3D(%s)" % self.name


class Input:

    __slots__ = ("_native",)

    def __init__(self, native):
        self._native = native

    def key(self, name):
        return self._native.isKeyDown(_key_code(name))

    def key_pressed(self, name):
        return self._native.wasKeyPressed(_key_code(name))

    def key_released(self, name):
        return self._native.wasKeyReleased(_key_code(name))

    def mouse(self, button="left"):
        return self._native.isMouseButtonDown(_mouse_button(button))

    def mouse_pressed(self, button="left"):
        return self._native.wasMouseButtonPressed(_mouse_button(button))

    @property
    def cursor(self):
        return (self._native.cursorX(), self._native.cursorY())

    @property
    def mouse_delta(self):
        return (self._native.mouseDeltaX(), self._native.mouseDeltaY())

    @property
    def scroll(self):
        return self._native.scrollDeltaY()


_key_cache = {}
_button_cache = {}


def _key_code(name):
    found = _key_cache.get(name)
    if found is None:
        upper = str(name).upper()
        if len(upper) == 1 and upper.isdigit():
            upper = "KEY_" + upper
        found = getattr(_KeyCode, upper)
        _key_cache[name] = found
    return found


def _mouse_button(name):
    found = _button_cache.get(name)
    if found is None:
        found = getattr(_MouseButton, str(name).upper())
        _button_cache[name] = found
    return found


class Export:

    def __init__(self, default, label="", minimum=0.0, maximum=0.0, step=0.0, color=False):
        self.default = default
        self.label = label
        self.minimum = minimum
        self.maximum = maximum
        self.step = step
        self.color = color


def export(default, label="", minimum=0.0, maximum=0.0, step=0.0, color=False):
    return Export(default, label, minimum, maximum, step, color)


def component(name=None, category="Scripts", description=""):
    def decorate(target):
        exports = []
        for attribute in list(vars(target)):
            value = getattr(target, attribute)
            if isinstance(value, Export):
                exports.append((attribute, value))
                setattr(target, attribute, value.default)
        target._epysia_meta = {
            "name": name or target.__name__,
            "category": category,
            "description": description,
            "exports": exports,
        }
        _registered.append(target)
        return target

    return decorate


class Behaviour:

    game_object = None
    services = None
    _native_transform = None

    @property
    def object(self):
        return Object3D(self.game_object)

    @property
    def transform(self):
        return Transform(self._transform())

    @property
    def position(self):
        return Vec3.of(self._transform().position())

    @position.setter
    def position(self, value):
        self._transform().setPosition(_f(value.x), _f(value.y), _f(value.z))

    def _transform(self):
        native = self._native_transform
        if native is None:
            native = self.game_object.transform3DOrNull()
            self._native_transform = native
        return native

    def find(self, name):
        found = self.services.scene().findByName(name)
        return Object3D(found.get()) if found.isPresent() else None

    def _epysia_update(self, native_input, delta_seconds):
        self.on_update(Input(native_input), delta_seconds)

    def on_start(self):
        pass

    def on_update(self, input, delta_seconds):
        pass

    def on_fixed_update(self, fixed_step_seconds):
        pass

    def on_destroy(self):
        pass
