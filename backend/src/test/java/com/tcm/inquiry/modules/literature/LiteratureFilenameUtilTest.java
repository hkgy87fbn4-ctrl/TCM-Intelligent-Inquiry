package com.tcm.inquiry.modules.literature.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LiteratureFilenameUtilTest {

    @Test
    void sanitize() {
        assertThat(LiteratureFilenameUtil.sanitize(null)).isEqualTo("upload.bin");
        assertThat(LiteratureFilenameUtil.sanitize("  ")).isEqualTo("upload.bin");
        assertThat(LiteratureFilenameUtil.sanitize("a/b\\百合.txt")).contains("百合");
        assertThat(LiteratureFilenameUtil.sanitize("../../etc")).doesNotContain("..");
    }
}
