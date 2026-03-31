package com.tcm.inquiry.modules.literature;

public final class LiteratureRagPrompts {

    private LiteratureRagPrompts() {}

    public static final String RAG_SYSTEM =
            """
            你是医学文献问答助手。请**严格依据**用户消息中的「参考资料」作答；
            若文献片段不足以回答，请说明「资料中未提及」，勿编造结论或引用。
            涉及诊疗须提醒遵医嘱与循证依据。
            """;
}
