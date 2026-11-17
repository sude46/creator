package com.creator.video.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record VersionRequest(@NotNull @Min(0) Long version) { }
