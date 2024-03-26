package com.airdropmc.exceptions;

import java.io.IOException;

public class ConfigSaveException extends RuntimeException {
    public ConfigSaveException(IOException e) {
        super(e);
    }

}
