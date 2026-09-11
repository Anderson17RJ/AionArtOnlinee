package com.aionemu.gameserver.dataholders;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import com.aionemu.gameserver.model.templates.SkillSkinTemplate;

@XmlRootElement(name = "skill_skins")
@XmlAccessorType(XmlAccessType.FIELD)
public class SkillSkinData {

	@XmlElement(name = "skill_skin")
	private List<SkillSkinTemplate> templates;
	@XmlTransient
	private final Map<Integer, SkillSkinTemplate> skins = new HashMap<>();

	void afterUnmarshal(Unmarshaller unmarshaller, Object parent) {
		for (SkillSkinTemplate template : templates)
			skins.put(template.getId(), template);
		templates = null;
	}

	public SkillSkinTemplate getSkillSkinTemplate(int skinId) {
		return skins.get(skinId);
	}

	public int size() {
		return skins.size();
	}
}
