package fr.epistudio.epysia.audio;

import fr.epistudio.epysia.exceptions.EpysiaException;
import org.lwjgl.openal.EXTEfx;
import org.lwjgl.openal.AL10;

public final class AudioReverbZone {

    private final int effectId;
    private final int slotId;

    public AudioReverbZone() {
        effectId = EXTEfx.alGenEffects();
        EXTEfx.alEffecti(effectId, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_EAXREVERB);
        if (EXTEfx.alGetEffecti(effectId, EXTEfx.AL_EFFECT_TYPE) != EXTEfx.AL_EFFECT_EAXREVERB) {
            EXTEfx.alEffecti(effectId, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_REVERB);
        }
        slotId = EXTEfx.alGenAuxiliaryEffectSlots();
        applyPreset(AudioReverbPreset.GENERIC);
        EXTEfx.alAuxiliaryEffectSloti(slotId, EXTEfx.AL_EFFECTSLOT_EFFECT, effectId);
    }

    public void applyPreset(AudioReverbPreset preset) {
        boolean isEax = EXTEfx.alGetEffecti(effectId, EXTEfx.AL_EFFECT_TYPE) == EXTEfx.AL_EFFECT_EAXREVERB;
        if (isEax) {
            applyEaxParameters(preset);
        } else {
            applyBasicParameters(preset);
        }
        EXTEfx.alAuxiliaryEffectSloti(slotId, EXTEfx.AL_EFFECTSLOT_EFFECT, effectId);
        int error = AL10.alGetError();
        if (error != AL10.AL_NO_ERROR) {
            throw new EpysiaException("Failed to apply reverb preset (AL error " + error + ").");
        }
    }

    private void applyEaxParameters(AudioReverbPreset preset) {
        EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_DENSITY, preset.density());
        EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_DIFFUSION, preset.diffusion());
        EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_GAIN, preset.gain());
        EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_GAINHF, preset.gainHighFrequency());
        EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_DECAY_TIME, preset.decayTimeSeconds());
        EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_DECAY_HFRATIO, preset.decayHighFrequencyRatio());
        EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_REFLECTIONS_GAIN, preset.reflectionsGain());
        EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_REFLECTIONS_DELAY, preset.reflectionsDelaySeconds());
        EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_LATE_REVERB_GAIN, preset.lateReverbGain());
        EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_LATE_REVERB_DELAY, preset.lateReverbDelaySeconds());
        EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_AIR_ABSORPTION_GAINHF, preset.airAbsorptionGainHighFrequency());
        EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_ROOM_ROLLOFF_FACTOR, preset.roomRolloffFactor());
    }

    private void applyBasicParameters(AudioReverbPreset preset) {
        EXTEfx.alEffectf(effectId, EXTEfx.AL_REVERB_DENSITY, preset.density());
        EXTEfx.alEffectf(effectId, EXTEfx.AL_REVERB_DIFFUSION, preset.diffusion());
        EXTEfx.alEffectf(effectId, EXTEfx.AL_REVERB_GAIN, preset.gain());
        EXTEfx.alEffectf(effectId, EXTEfx.AL_REVERB_GAINHF, preset.gainHighFrequency());
        EXTEfx.alEffectf(effectId, EXTEfx.AL_REVERB_DECAY_TIME, preset.decayTimeSeconds());
        EXTEfx.alEffectf(effectId, EXTEfx.AL_REVERB_DECAY_HFRATIO, preset.decayHighFrequencyRatio());
        EXTEfx.alEffectf(effectId, EXTEfx.AL_REVERB_REFLECTIONS_GAIN, preset.reflectionsGain());
        EXTEfx.alEffectf(effectId, EXTEfx.AL_REVERB_REFLECTIONS_DELAY, preset.reflectionsDelaySeconds());
        EXTEfx.alEffectf(effectId, EXTEfx.AL_REVERB_LATE_REVERB_GAIN, preset.lateReverbGain());
        EXTEfx.alEffectf(effectId, EXTEfx.AL_REVERB_LATE_REVERB_DELAY, preset.lateReverbDelaySeconds());
        EXTEfx.alEffectf(effectId, EXTEfx.AL_REVERB_AIR_ABSORPTION_GAINHF, preset.airAbsorptionGainHighFrequency());
        EXTEfx.alEffectf(effectId, EXTEfx.AL_REVERB_ROOM_ROLLOFF_FACTOR, preset.roomRolloffFactor());
    }

    public int slotId() {
        return slotId;
    }

    public void destroy() {
        EXTEfx.alDeleteAuxiliaryEffectSlots(slotId);
        EXTEfx.alDeleteEffects(effectId);
    }
}
