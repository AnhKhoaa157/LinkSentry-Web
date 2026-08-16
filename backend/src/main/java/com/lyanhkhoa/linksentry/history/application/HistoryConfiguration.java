package com.lyanhkhoa.linksentry.history.application;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the hourly retention job for persisted scan snapshots. */
@Configuration
@EnableScheduling
public class HistoryConfiguration {}
