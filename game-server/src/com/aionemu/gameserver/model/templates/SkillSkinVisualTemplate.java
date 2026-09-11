package com.aionemu.gameserver.model.templates;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

/** Client-only clone used to render one concrete skill/stage of a skill skin. */
@XmlAccessorType(XmlAccessType.FIELD)
public class SkillSkinVisualTemplate {

	@XmlAttribute(name = "original_skill_id", required = true)
	private int originalSkillId;
	@XmlAttribute(name = "visual_skill_id", required = true)
	private int visualSkillId;

	public int getOriginalSkillId() { return originalSkillId; }
	public int getVisualSkillId() { return visualSkillId; }
}
