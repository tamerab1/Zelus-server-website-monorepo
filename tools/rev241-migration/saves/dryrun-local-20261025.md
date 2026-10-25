# Rev 241 save migration -- dry run report

Read-only. Data: `osrs server/data zelus rev241/data`. Manifest: `remap-241.json`.

- JSON files scanned: **1108** (0 unreadable; 6 empty files passed through unchanged)
- Remap tables used: item (730 ids), npc (39 ids), obj (491 ids), varbit (8 ids), varp (9 ids), seq (11 ids), spotanim (100 ids)
- Simulation: 1108 files; 532 item fields, 0 varp slots and 0 packed var keys would change; **0 invariant violations**

## Verdict

**READY**: every path holding a colliding id is classified and the simulated remap preserves every item stack.

## Unclassified paths (need a rule)

(none)

## Classified paths

| class | file group | JSON path | hits | files | id types matched | sample values |
|---|---|---|---|---|---|---|
| ignore | players | `$.akkhaBestTime` | 1 | 1 | item | 31201 |
| ignore | players | `$.bank.items[].amount` | 1 | 1 | obj | 60000 |
| ignore | players | `$.dailyTasksCompletionReward.amount` | 1 | 1 | spotanim | 4281 |
| ignore | players | `$.savedConfigs.{key}` | 1 | 1 | spotanim | 4200 |
| item | players | `$.bank.items[].id` | 428 | 4 | item, obj, spotanim | 33000, 31834, 33002, 33003, 33001, 33018 |
| item | players | `$.currentDailyRewards[].id` | 2 | 2 | varp | 4835 |
| item | players | `$.equipment.items[].id` | 12 | 5 | item, obj | 31836, 33021, 33000, 33002, 33003, 60010 |
| item | players | `$.gearPresets[].equipment.{key}.id` | 6 | 1 | item, obj | 31688, 31408, 31644, 31642, 60335, 60336 |
| item | players | `$.gearPresets[].inventory[].id` | 4 | 1 | obj | 60168, 60166 |
| item | players | `$.inventory.items[].id` | 16 | 2 | item | 31774, 31818, 31778, 31650, 31662, 31660 |
| item | players | `$.petItemIdsObtained[]` | 385 | 3 | obj, item | 60247, 34042, 33025, 60246, 33023, 33026 |
| item | players | `$.uniqueDrops.{key}` (key) | 141 | 3 | item, obj | 33006, 33018, 33031, 33004, 33023, 33024 |
| item | players | `$.wildernessKeyItems[][].id` | 1 | 1 | item | 33002 |
| varp-index-array | players | `$.varps[]` | 41 | 41 | obj | 60000 |


## Empty files (copied through unchanged)

- `runtime\saves\attributes\clanchat.FriendChatPlayer\don kee.json`
- `runtime\saves\attributes\collectionlog.CollectionLog\don kee.json`
- `runtime\saves\attributes\io.ruin.model.activities.wilderness.EscapeCaves$EscapeCave\don kee.json`
- `runtime\saves\attributes\io.ruin.model.entity.player.groupironmode.GroupBank\don kee.json`
- `runtime\saves\attributes\player.chat.filter.attribute.ChatFilterAttribute\don kee.json`
- `runtime\saves\friendlist\don kee.json`
