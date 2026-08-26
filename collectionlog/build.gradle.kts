plugins {
	id("reason-kotlin")
}

dependencies {
	implementation(projects.playerAttributesApi)
	implementation(projects.kronosServerDiscordServices)
	implementation(libs.json)
}
