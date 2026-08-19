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

_camel_cache = {}


def _camel(name):
    found = _camel_cache.get(name)
    if found is None:
        head, _, tail = name.partition("_")
        found = head + "".join(part[:1].upper() + part[1:] for part in tail.split("_") if part)
        _camel_cache[name] = found
    return found


def _from_java(value):
    if value is None:
        return None
    if isinstance(value, (bool, int, float, str)):
        return value
    if _is_vector(value):
        return Vec3(value.x, value.y, value.z)
    if _host.isOptional(value):
        return wrap(_host.unwrapOptional(value))
    if _host.isCollection(value):
        return [wrap(item) for item in value]
    return wrap(value)


def _is_vector(value):
    return _host.isVector3(value)


def _to_java(value):
    if isinstance(value, Vec3):
        return value.to_java()
    if isinstance(value, Native):
        return value.native
    if isinstance(value, float):
        return _f(value)
    return value


def wrap(value):
    if value is None or isinstance(value, (bool, int, float, str)):
        return value
    return Native(value)


class Native:

    __slots__ = ("_native",)

    def __init__(self, native):
        object.__setattr__(self, "_native", native)

    @property
    def native(self):
        return object.__getattribute__(self, "_native")

    def __getattr__(self, name):
        target = self._member(name)
        if target is None:
            raise AttributeError("%s has no %s" % (self._type_name(), name))

        def call(*arguments):
            return _from_java(_invoke(target, arguments))

        return call

    def __setattr__(self, name, value):
        declared = getattr(type(self), name, None)
        if isinstance(declared, property) and declared.fset is not None:
            declared.fset(self, value)
            return
        setter = self._member("set_" + name)
        if setter is None:
            raise AttributeError("%s cannot set %s" % (self._type_name(), name))
        _invoke(setter, (value,))

    def _member(self, name):
        native = object.__getattribute__(self, "_native")
        for candidate in (_camel(name), name, "get" + _camel(name)[:1].upper() + _camel(name)[1:]):
            found = getattr(native, candidate, None)
            if found is not None:
                return found
        return None

    def _type_name(self):
        return _host.typeNameOf(object.__getattribute__(self, "_native"))

    def __repr__(self):
        return "%s(%s)" % (self._type_name(), object.__getattribute__(self, "_native"))


def _invoke(target, arguments):
    converted = [_to_java(argument) for argument in arguments]
    try:
        return target(*converted)
    except TypeError:
        spread = _spread(converted)
        if spread is None:
            raise
        return target(*spread)


def _spread(arguments):
    if len(arguments) != 1 or not _is_vector(arguments[0]):
        return None
    vector = arguments[0]
    return (vector.x, vector.y, vector.z)


class Transform(Native):

    __slots__ = ()

    @property
    def position(self):
        return Vec3.of(self.native.position())

    @position.setter
    def position(self, value):
        self.native.setPosition(_f(value.x), _f(value.y), _f(value.z))

    @property
    def scale(self):
        return Vec3.of(self.native.scale())

    @scale.setter
    def scale(self, value):
        self.native.setScale(_f(value.x), _f(value.y), _f(value.z))

    @property
    def world_position(self):
        return Vec3.of(self.native.worldPosition(_Vector3f()))

    def move(self, delta):
        self.native.translate(_f(delta.x), _f(delta.y), _f(delta.z))

    def rotate(self, pitch=0.0, yaw=0.0, roll=0.0):
        self.native.setRotationEuler(_f(math.radians(pitch)), _f(math.radians(yaw)),
                                     _f(math.radians(roll)))

    def look_at(self, target, up=UP):
        self.native.lookAt(_f(target.x), _f(target.y), _f(target.z), _f(up.x), _f(up.y), _f(up.z))


_key_cache = {}
_button_cache = {}


class Input(Native):

    __slots__ = ()

    def key(self, name):
        return self.native.isKeyDown(_key_code(name))

    def key_pressed(self, name):
        return self.native.wasKeyPressed(_key_code(name))

    def key_released(self, name):
        return self.native.wasKeyReleased(_key_code(name))

    def mouse(self, button="left"):
        return self.native.isMouseButtonDown(_mouse_button(button))

    def mouse_pressed(self, button="left"):
        return self.native.wasMouseButtonPressed(_mouse_button(button))

    @property
    def cursor(self):
        return (self.native.cursorX(), self.native.cursorY())

    @property
    def mouse_delta(self):
        return (self.native.mouseDeltaX(), self.native.mouseDeltaY())

    @property
    def scroll(self):
        return self.native.scrollDeltaY()


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
    _transform = None

    @property
    def object(self):
        return Native(self.game_object)

    @property
    def transform(self):
        found = self._transform
        if found is None:
            found = Transform(self.game_object.transform3DOrNull())
            self._transform = found
        return found

    @property
    def position(self):
        return self.transform.position

    @position.setter
    def position(self, value):
        self.transform.position = value

    def find(self, name):
        found = self.services.scene().findByName(name)
        return Native(found.get()) if found.isPresent() else None

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
