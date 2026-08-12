package fr.epistudio.epysia.steam;

import fr.epistudio.epysia.exceptions.EpysiaException;

public final class SteamUnavailableException extends EpysiaException {

    public SteamUnavailableException(String message) {
        super(message);
    }
}
