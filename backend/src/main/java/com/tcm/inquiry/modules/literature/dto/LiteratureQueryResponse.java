package com.tcm.inquiry.modules.literature.dto;

import java.util.List;

public record LiteratureQueryResponse(String answer, List<String> sources, int retrievedChunks) {}
