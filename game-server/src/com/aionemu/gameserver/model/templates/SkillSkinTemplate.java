package com.aionemu.gameserver.model.templates;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

/** Definition of a cosmetic replacement for one skill group. */
@XmlAccessorType(XmlAccessType.FIELD)
public class SkillSkinTemplate {

	@XmlAttribute(name = "id", required = true)
	private int id;
	@XmlAttribute(name = "name", required = true)
	private String name;
	@XmlAttribute(name = "skill_group", required = true)
	private String skillGroup;
	/** Legacy one-clone mapping. Prefer explicit <skill> mappings for multi-stage skills. */
	@XmlAttribute(name = "visual_skill_id")
	private int visualSkillId;
	@XmlAttribute(name = "motion_name", required = true)
	private String motionName;
	@XmlAttribute(name = "ammo_speed", required = true)
	private int ammoSpeed;
	@XmlElement(name = "skill")
	private List<SkillSkinVisualTemplate> visualSkills;

	public int getId() { return id; }
	public String getName() { return name; }
	public String getSkillGroup() { return skillGroup; }
	public int getVisualSkillId() { return visualSkillId; }
	public String getMotionName() { return motionName; }
	public int getAmmoSpeed() { return ammoSpeed; }

	/**
	 * Resolves the clone for one actual server skill id. Explicit mappings are intentionally
	 * selective, so an unlisted chain stage keeps its normal client visual.
	 */
	public int getVisualSkillId(int originalSkillId) {
		if (visualSkills != null && !visualSkills.isEmpty()) {
			for (SkillSkinVisualTemplate visualSkill : visualSkills) {
				if (visualSkill.getOriginalSkillId() == originalSkillId)
					return visualSkill.getVisualSkillId();
			}
			return originalSkillId;
		}
		return visualSkillId > 0 ? visualSkillId : originalSkillId;
	}
}
