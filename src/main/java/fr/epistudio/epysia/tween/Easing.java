package fr.epistudio.epysia.tween;

public enum Easing {

    LINEAR {
        @Override
        public float apply(float progress) {
            return progress;
        }
    },
    QUAD_IN {
        @Override
        public float apply(float progress) {
            return progress * progress;
        }
    },
    QUAD_OUT {
        @Override
        public float apply(float progress) {
            return 1.0f - (1.0f - progress) * (1.0f - progress);
        }
    },
    QUAD_IN_OUT {
        @Override
        public float apply(float progress) {
            return progress < 0.5f
                    ? 2.0f * progress * progress
                    : 1.0f - squared(-2.0f * progress + 2.0f) * 0.5f;
        }
    },
    CUBIC_IN {
        @Override
        public float apply(float progress) {
            return progress * progress * progress;
        }
    },
    CUBIC_OUT {
        @Override
        public float apply(float progress) {
            return 1.0f - cubed(1.0f - progress);
        }
    },
    CUBIC_IN_OUT {
        @Override
        public float apply(float progress) {
            return progress < 0.5f
                    ? 4.0f * progress * progress * progress
                    : 1.0f - cubed(-2.0f * progress + 2.0f) * 0.5f;
        }
    },
    QUART_OUT {
        @Override
        public float apply(float progress) {
            float inverted = 1.0f - progress;
            return 1.0f - squared(squared(inverted));
        }
    },
    SINE_IN {
        @Override
        public float apply(float progress) {
            return 1.0f - (float) Math.cos(progress * Math.PI * 0.5);
        }
    },
    SINE_OUT {
        @Override
        public float apply(float progress) {
            return (float) Math.sin(progress * Math.PI * 0.5);
        }
    },
    SINE_IN_OUT {
        @Override
        public float apply(float progress) {
            return -(float) (Math.cos(Math.PI * progress) - 1.0) * 0.5f;
        }
    },
    EXPO_OUT {
        @Override
        public float apply(float progress) {
            return progress >= 1.0f ? 1.0f : 1.0f - (float) Math.pow(2.0, -10.0 * progress);
        }
    },
    CIRC_OUT {
        @Override
        public float apply(float progress) {
            return (float) Math.sqrt(1.0f - squared(progress - 1.0f));
        }
    },
    BACK_OUT {
        @Override
        public float apply(float progress) {
            float inverted = progress - 1.0f;
            return 1.0f + BACK_C3 * cubed(inverted) + BACK_C1 * squared(inverted);
        }
    },
    ELASTIC_OUT {
        @Override
        public float apply(float progress) {
            if (progress <= 0.0f || progress >= 1.0f) {
                return progress;
            }
            return (float) (Math.pow(2.0, -10.0 * progress)
                    * Math.sin((progress * 10.0 - 0.75) * ELASTIC_PERIOD) + 1.0);
        }
    },
    BOUNCE_OUT {
        @Override
        public float apply(float progress) {
            if (progress < 1.0f / BOUNCE_DIVISOR) {
                return BOUNCE_STRENGTH * progress * progress;
            }
            if (progress < 2.0f / BOUNCE_DIVISOR) {
                float shifted = progress - 1.5f / BOUNCE_DIVISOR;
                return BOUNCE_STRENGTH * shifted * shifted + 0.75f;
            }
            if (progress < 2.5f / BOUNCE_DIVISOR) {
                float shifted = progress - 2.25f / BOUNCE_DIVISOR;
                return BOUNCE_STRENGTH * shifted * shifted + 0.9375f;
            }
            float shifted = progress - 2.625f / BOUNCE_DIVISOR;
            return BOUNCE_STRENGTH * shifted * shifted + 0.984375f;
        }
    };

    private static final float BACK_C1 = 1.70158f;
    private static final float BACK_C3 = BACK_C1 + 1.0f;
    private static final double ELASTIC_PERIOD = 2.0 * Math.PI / 3.0;
    private static final float BOUNCE_DIVISOR = 2.75f;
    private static final float BOUNCE_STRENGTH = 7.5625f;

    public abstract float apply(float progress);

    public float at(float progress) {
        return apply(Math.clamp(progress, 0.0f, 1.0f));
    }

    private static float squared(float value) {
        return value * value;
    }

    private static float cubed(float value) {
        return value * value * value;
    }
}
