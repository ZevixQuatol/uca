package com.twlic.uca.base.registry;

public final class RegistryException extends RuntimeException {

    private final RegistryError error;

    public RegistryException(RegistryError error, String message) {
        super(message);
        this.error = error;
    }

    public RegistryError error() {
        return error;
    }
}
