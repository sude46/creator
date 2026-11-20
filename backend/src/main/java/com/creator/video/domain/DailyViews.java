package com.creator.video.domain;

import java.time.LocalDate;

public record DailyViews(LocalDate date, long views) { }
