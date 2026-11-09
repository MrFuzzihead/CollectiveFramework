# CollectiveFramework — Codebase Review

**Repository:** `MrFuzzihead/CollectiveFramework` (fork of `austinv11/CollectiveFramework`)
**Target:** Minecraft 1.7.10 / Forge 10.13.4.1614, GTNH RetroFuturaGradle buildscript
**Scope:** `src/main/java` — 93 Java files, 10,074 lines; plus resources, build config, CI
**Method:** full source read + cross-reference of every symbol's call sites; decompiled 1.7.10
`FMLCommonHandler` / `MinecraftForge` / `GuiConfig` / `NBTTagCompound` bytecode and sources to
confirm event-bus and API semantics. `./gradlew compileJava` passes, and checkstyle is clean —
**everything below is a logic/design defect, not a compile error.**

---

## 0. Executive summary

This is a *library* mod. Almost none of it is reachable from the mod's own entry point, which means
most defects here are "public API is wrong" rather than "the game crashes". That said, there are
**four user-facing features that cannot work as written**, two **security holes**, and a
**threading layer that is actively harmful** (100% CPU spin loops, `IllegalMonitorStateException`
on the documented API, and reflective access to `java.lang.Thread` internals that throws on
Java 17+).

| Severity | Count | Theme |
|---|---|---|
| **P0** | 20 | Broken features, crashes, exploits, data corruption |
| **P1** | 20 | Wrong results, silent data loss |
| **P2** | 30 | Robustness, edge cases, API smells |
| **Dead code** | ~25 items | Including 4 stale resource files and 4 stale lang keys |
| **Perf** | 14 | One is a per-frame disk read |

**Fix progress: 27 of 70 closed.** Batch 1 (§1.1, §1.2, §1.3, §1.4, §1.5, §1.18, plus §2.18 and the
`ITranslationProvider` signature), batch 2 (§1.7, §1.16, §1.17, plus the `fromBytes` half of §3.2),
batch 3 (§1.8, §1.9, §3.12), batch 4 (§1.11–§1.15, plus the `SimpleRunnable` half of §3.15) and
batch 5 (§3.1, §3.5–§3.8) are implemented and covered by tests — see §11.

The single highest-leverage fix is **#1 (event bus)** — it silently disables config sync, the time
buttons, the key overlay, and book interactivity in one shot. Second is **#2/#3 (config
registration + lang modid)** — together they make the entire config GUI empty *and* unreadable.

---

## 1. P0 — Critical

### 1.1 Five `@SubscribeEvent` handlers are registered on the wrong event bus

`MinecraftForge.EVENT_BUS` and `FMLCommonHandler.instance().bus()` are **two distinct
`EventBus` instances**. Verified from bytecode:

```
MinecraftForge.static{}      -> putstatic EVENT_BUS = new cpw.mods.fml.common.eventhandler.EventBus()
FMLCommonHandler.fireKeyInput()    -> invokevirtual bus() -> EventBus.post(...)
FMLCommonHandler.firePlayerLoggedIn() -> invokevirtual bus() -> EventBus.post(...)
FMLCommonHandler.onPreClientTick() -> invokevirtual bus() -> EventBus.post(...)
```

FML game events (`PlayerEvent`, `TickEvent`, `InputEvent`) are posted to the **FML** bus. Everything
in `CommonProxy`/`ClientProxy` is registered on the **Forge** bus.

| Handler | File:line | Event | Result |
|---|---|---|---|
| `onServerJoin` | `CollectiveFramework.java:97` | `PlayerEvent.PlayerLoggedInEvent` | **never fires** |
| `onClientDisconnect` | `CollectiveFramework.java:110` | `PlayerEvent.PlayerLoggedOutEvent` | **never fires** |
| `onClientTick` | `event/handler/ClientTickHandler.java:24` | `TickEvent.ClientTickEvent` | **never fires** |
| `onKeyInput` | `event/handler/KeyHandler.java:14` | `InputEvent.KeyInputEvent` | **never fires** |
| `onMouseInput` | `event/handler/KeyHandler.java:27` | `InputEvent.MouseInputEvent` | **never fires** |
| `onKeyboardInput` | `books/core/GuiBook.java:219` | `InputEvent.KeyInputEvent` | **never fires** |

**Consequences:**

* Server→client config sync (the entire `ConfigPacket` mechanism) is dead code. Configs are never
  pushed on login and never reverted on logout.
* `Config.enableButtonTimeChanging` (F8/F9 time scrubbing) does nothing — and
  `TimeUpdatePacket` is therefore never sent, which is the only thing that made
  `cf_at.cfg`'s `RenderGlobal.field_72773_u` access transformer necessary. The AT is dead too.
* `Config.keyOverlay` never renders anything (`KeyHandler` never queues a keypress).
* `InteractiveEntry.onKeyboardEvent` is never called, so the book API's interactivity is a no-op.

`GuiBook.onMouseInput` (line 230) uses `net.minecraftforge.client.event.MouseEvent`, which *is* on
the Forge bus — so mouse input works while keyboard input does not. That asymmetry is a strong
hint this was never tested end-to-end.

**Fix:** register FML-bus handlers via `FMLCommonHandler.instance().bus().register(...)`.

---

### 1.2 The config GUI is always empty

```java
// CollectiveFramework.java:54
ConfigRegistry.registerConfig(new Config());

// reference/Config.java:10
public static final Config INSTANCE = new Config();

// client/gui/GuiConfig.java:11
super(parentScreen, Reference.MOD_ID, Reference.MOD_NAME, Config.INSTANCE);
```

`ConfigRegistry` matches the GUI's config object against the registered one by **identity**:

```java
// ConfigRegistry.java:143 (also :159 onGuiClosed, :170 getFilePath)
if (proxy.config.equals(configObject)) { ... }
```

`Config` does not override `equals`, so `new Config() != Config.INSTANCE`. Result:

* `getCategories` returns an empty list → the config screen shows **no categories at all**.
* `onGuiClosed` is a no-op → edits could never be saved even if they were shown.
* `getFilePath` returns `null` → `GuiConfig.titleLine2` is `null` (harmless, the parent guards it).

**Fix:** register `Config.INSTANCE`, or better, key `ConfigProxy` lookups by
`config.getClass()` and drop `Object.equals` entirely.

---

### 1.3 Config GUI language keys use the wrong mod id

The lang file declares `theframework.lang.config.*`, but the GUI builds keys from
`Reference.MOD_ID`:

```java
// Reference.java:7
public static final String MOD_ID = "collectiveframework";

// ConfigRegistry.java:295
String languageKey = String.format("%s.lang.config.%s", modid, categoryName);
```

So every category and every property renders as its raw key
(`collectiveframework.lang.config.Misc.restrictThreadUsage`) instead of a label.

Related: the asset domain is `assets/theframework/` while the mod id is `collectiveframework`.
Lang files still load (1.7.10's `Locale.loadLocaleDataFiles` iterates *all* resource domains — see
`Locale.java:32-56`), but any future texture/model/`IconManager` resource will be invisible.

**Fix:** rename `assets/theframework` → `assets/collectiveframework` and rewrite the lang prefix.

---

### 1.4 `DefaultConfigurationHandler.getValue` returns a `Field`, not the value

```java
// ConfigRegistry.java:227-235
public Object getValue(String configValue, String category, Object config) {
    if (hasValue(configValue, category)) {
        if (current.containsKey(category) && current.get(category).containsKey(configValue))
            return current.get(category).get(configValue);   // <-- a java.lang.reflect.Field
    }
    return null;
}
```

Every caller of `IConfigurationHandler#getValue` (documented as "Called when a value is requested …
@return The value") gets a `Field` object. Any `(Integer) handler.getValue(...)` throws
`ClassCastException`.

---

### 1.5 Config file corruption: the closing brace is conditional on field count

```java
// ConfigRegistry.java:390-401
if (f.isAnnotationPresent(Description.class) && ...clientSideOnly() && proxy.getSide() == Side.SERVER)
    continue;                                     // skipped, `i` not incremented
if (i == 0) writer.println(category + " {");
...
i++;
if (i == current.get(category).size()) writer.println("}");
```

If the **last** field in a category is `clientSideOnly` and we are on a dedicated server, `i` never
reaches `size()` and the `}` is never written. The file is then structurally invalid, and on the
next load `readFromReader`'s state machine desynchronises and silently drops every subsequent
value. Currently latent (all `clientSideOnly` fields happen to be mid-category), but one field
reorder away from silent config loss.

---

### 1.6 `readFromReader`'s parser is a 3-line state machine with no error handling

```java
// ConfigRegistry.java:423-463
if (!reachedBracket && line.contains("{")) { reachedBracket = true; continue; }
if (reachedBracket && line.equals("}"))   { reachedBracket = false; continue; }
if (reachedBracket) {
    if (lineCount < 1) lineCount++;
    else if (lineCount == 1) { /* comment skip OR parse */ lineCount++; }
    else lineCount = 0;
}
```

It assumes *exactly* `{`, comment, `key:field=value`, blank — forever. Any deviation (multi-line
comment, a comment containing `{`, a skipped `clientSideOnly` field, a hand-edited file, a value
containing `=` before the intended one) silently skips or misassigns values. There is no logging
and no validation. Also: `f.set(config, deserialize(key, line))` at line 452 only catches
`ConfigException` — an `IllegalArgumentException` from a type mismatch propagates out of the mod's
`preInit`.

**Fix:** use Forge's `Configuration`/`Property` (already on the classpath) or Gson.

---

### 1.7 `TileEntityClientUpdatePacket` NPEs on every client

```java
// network/TileEntityClientUpdatePacket.java:38
world = WorldUtils.getWorldFromDimensionId(tag.getInteger("dim"));

// utils/WorldUtils.java:36-39
public static World getWorldFromDimensionId(int id) {
    return MinecraftServer.getServer().worldServerForDimension(id);   // null on a client
}
```

`MinecraftServer.getServer()` is `null` on a physical client. This packet is registered as
`Side.CLIENT` (`CollectiveFramework.java:86-90`), i.e. it is *only* ever decoded on a client. The
class can never work.

**Fix:** on the client use `Minecraft.getMinecraft().theWorld` (and validate the dimension id).

**Fixed in batch 2 (§11).** The dimension now travels on the wire as `dimensionId` and the handler
resolves the world from the client. `WorldUtils.getWorldFromDimensionId` is still public API and still
NPEs on a client, but the framework no longer calls it there — external callers should guard it.

---

### 1.8 `TranslationManager` provider fallback is inverted and out-of-bounds

```java
// language/TranslationManager.java:52-60 (same bug at :87 and :116)
while (unusableProviders.contains(translators.get(i).getProviderName())
        || !translators.get(i).canDetectLanguage()) {
    i++;
    if (!translators.contains(i))                                  // List<ITranslationProvider>
        throw new TranslationException(ErrorTypes.NO_VALID_PROVIDERS);
}
```

Two bugs in five lines:

1. `translators.contains(i)` boxes `i` into an `Integer` and asks a
   `List<ITranslationProvider>` if it contains that `Integer` — **always `false`**. So the moment
   the first provider is unusable (or cannot detect a language), it throws
   `NO_VALID_PROVIDERS` instead of trying the next one. There is exactly one provider
   (`YandexProvider`), so the loop is a de-facto `throw`.
2. `translators.get(i)` is evaluated **before** the bounds check, so with ≥1 unusable provider it
   throws `IndexOutOfBoundsException` rather than the intended exception.

Also `if (!translators.contains(i))` should be `if (i >= translators.size())`.

---

### 1.9 Translation cache is keyed on text only

```java
// TranslationManager.java:49, 64, 84, 97
if (translationCache.containsKey(text)) return translationCache.get(text);
...
translationCache.put(text, translation);
```

The key omits `toLang` (and `fromLang`). Translate `"Stone"` → `de`, then `"Stone"` → `fr`, and the
second call returns the German string. Additionally:

* The cache is an unbounded `HashMap` — a long play session with chat translation on is a memory leak.
* It is a plain `HashMap` mutated from both the render thread and the netty thread.

---

### 1.10 `StringUtils.replaceFirstPreservingCase` always throws

```java
// StringUtils.java:126-128
int index = temp.indexOf(toReplace.toLowerCase());
if (index == -1) return string;
String found = string.substring(index, toReplace.length());   // <-- should be index + toReplace.length()
```

Any match at `index > 0` produces `StringIndexOutOfBoundsException` (`begin > end`).
`replaceAllPreservingCase` (line 149) calls it in a loop, so it always throws for real input.

---

### 1.11 `SimpleRunnable.delay(int)` always throws `IllegalMonitorStateException`

```java
// multithreading/SimpleRunnable.java:85-87
public final void delay(int delay) throws InterruptedException {
    getThread().wait(delay);          // wait() without owning the monitor
}
```

`Object.wait()` requires holding the monitor of the object. Also semantically wrong: it would block
the *caller*, not the target thread. Use `LockSupport.parkNanos` or a `BlockingQueue.poll(timeout)`.

---

### 1.12 `HeavyCalculations.FutureImpl` — busy-wait + `IllegalMonitorStateException`

```java
// multithreading/HeavyCalculations.java:214-229
public V get() { while (!isDone) {} ... }                       // 100% CPU spin

public V get(long timeout, TimeUnit unit) {
    timeout = unit.toMillis(timeout);
    while (!isDone && timeout > 0) { timeout--; this.wait(1); } // no synchronized -> IMSE
    ...
}
```

`get(timeout, unit)` throws `IllegalMonitorStateException` on the first iteration, and never throws
`TimeoutException` even when it should. `get()` pegs a core. `isCancelled` is also not `volatile`
(line 183) while `isDone` is.

---

### 1.13 `SimpleThread` is a CPU spin loop that also breaks ticking

```java
// multithreading/SimpleThread.java:47-70
public void run() {
    do {
        if (started) {
            if (delay == -1) {
                if (isActive) super.run();          // no sleep -> hot loop
            } else {
                while (true) {
                    if (isActive) { super.run(); sleep(delay); }
                    else break;
                }
            }
        }
    } while (SimpleRunnable.RESTRICT_THREAD_USAGE);
}
```

* `RESTRICT_THREAD_USAGE` defaults to `true` (`SimpleRunnable.java:11`, and
  `Config.restrictThreadUsage = true`). After `disable()` sets `started = false`, the outer
  `do/while` becomes `while (true) { if (false) {} }` — **an infinite loop with no sleep**, one
  spinning thread per disabled `SimpleRunnable`.
  `Book.setData` (`books/api/Book.java:121`) calls `this.disable(true)` at the end of every save, and
  `DependencyManager.DownloadFuture.DownloadRunnable` does the same after every download, so
  spinning threads accumulate for the whole session.
* `RESTRICT_THREAD_USAGE == false` inverts it: the loop exits after one pass, so the thread dies
  after a single `run()`. Every ticking/repeating task silently stops. `HeavyCalculations` then
  processes at most one calculation per thread, forever.

---

### 1.14 `SimpleThread` reflectively mutates `java.lang.Thread` internals

```java
// multithreading/SimpleThread.java:95-105
Field field = Thread.class.getDeclaredField("target");
field.setAccessible(true);
...
Field field = Thread.class.getDeclaredField("threadStatus");
field.setAccessible(true);
```

`java.base` does not open `java.lang` to unnamed modules on JDK 9+. On **Java 17/21** (what GTNH
1.7.10 actually runs on) this throws `InaccessibleObjectException` unless the launcher passes
`--add-opens java.base/java.lang=ALL-UNNAMED`. `SimpleRunnable.getThread()` calls `setTarget` on
every pooled-thread reuse, and `SimpleThread.start()` calls `getStatus()` on every start. This is a
hard blocker on modern JVMs.

---

### 1.15 Thread pooling never actually reuses a thread

```java
// multithreading/SimpleRunnable.java:122-152
SimpleThread thread = entry.getValue();
thread.isActive = true;
thread.setName(getName());
thread.setTarget(this);              // swap the target of a *terminated* Thread
```

```java
// multithreading/SimpleThread.java:72-86
public synchronized void start() {
    int status = getStatus();
    if (status != 0) { isActive = true; }   // already started -> never calls super.start()
    else { super.start(); }
}
```

A terminated `Thread` cannot be restarted, and `start()` deliberately doesn't try. So a reused
`SimpleRunnable` never runs. Meanwhile `SimpleRunnable.thread` (the pool index) is never reset, so
two runnables can hold the same index and compare `equals()` to each other
(`SimpleRunnable.java:161-167`).

---

### 1.16 SECURITY — `TimeUpdatePacket` trusts a client-supplied identity

```java
// network/TimeUpdatePacket.java:57-62
if (Config.enableButtonTimeChanging && MinecraftServer.getServer()
        .getConfigurationManager().func_152596_g(message.profile)) {     // op check on packet data
    for (WorldServer world : MinecraftServer.getServer().worldServers)
        world.setWorldTime(message.startTime + message.time);
}
```

The op check is performed against the `GameProfile` **inside the packet**, not against the
connection. Any non-op client can craft a `TimeUpdatePacket` carrying an op's UUID/name and set the
world time to any `long`, on **every** world (`worldServers`, not the sender's world). No
magnitude/rate limit either.

Also `fromBytes` (`:37`) does `UUID.fromString(...)` with no guard → `IllegalArgumentException` on
a malformed payload, thrown on the netty event loop.

**Fix:** use `ctx.getServerHandler().playerEntity` and its `GameProfile`; ignore the packet's
profile entirely.

---

### 1.17 SECURITY — `TileEntityServerUpdatePacket` writes arbitrary client NBT

```java
// network/TileEntityServerUpdatePacket.java:60-62
message.world.getTileEntity(message.x, message.y, message.z).readFromNBT(message.updateData);
```

A client can target **any** tile entity in **any** dimension (the dimension id comes from the
packet) and feed it arbitrary NBT. For any mod TE that trusts its NBT (most chests, tanks, energy
cells), this is item duplication / inventory corruption. There is no distance check, no ownership
check, and no null check on `getTileEntity`.

`TileEntityClientUpdatePacket` has the same null-deref and the same dimension-id trust issue.

**Fix:** resolve the TE from `ctx`'s player's world, verify `player.getDistanceSq(te) < 64`, and
route through a whitelisted `INBTSyncable` interface rather than raw `readFromNBT`.

---

### 1.18 Hardcoded Yandex API key; the config option for it is dead

```java
// language/translation/YandexProvider.java:14
protected static final String API_KEY = "trnsl.1.1.20150228T025035Z.3525a3f37289aaaa.4964142c63fd40463bc91ea39e42363dd16a6a91";
```

A live credential committed to a public repo (revoke it). Meanwhile:

```java
// reference/Config.java:54-58
@Description(comment = "Valid API key used for Yandex translations", ...)
public static String yandexApiKey = "";
```

`grep -rn yandexApiKey src` → **one hit** (the declaration). The setting is never read, so users can
never supply their own key.

---

### 1.19 `Encryption.encrypt`/`decrypt` corrupt binary ciphertext

```java
// utils/security/Encryption.java:52-55
byte[] encrypted = new byte[cipher.getOutputSize(data.length)];
int encryptionLength = cipher.update(data, 0, data.length, encrypted, 0);
encryptionLength += cipher.doFinal(encrypted, encryptionLength);
return newSecretValue(new String(encrypted), key, encryptionLength);   // lossy!

// :80, :88-90
byte[] data = string.getBytes();                                       // lossy round-trip
```

`new String(byte[])` decodes using the platform charset and replaces malformed UTF-8 with
`U+FFFD`; `String.getBytes()` cannot recover the original bytes. So `decrypt(encrypt(x)) != x` for
essentially all ciphers. `encrypt` also wraps the *entire* output buffer, including the zero padding
past `encryptionLength`.

**Fix:** return `Base64.getEncoder().encodeToString(...)` (or a `byte[]`), and use explicit charsets
everywhere.

---

### 1.20 `Book.setPage` off-by-one → guaranteed `ArrayIndexOutOfBoundsException`

```java
// books/api/Book.java:161-170
if (newPage >= getPages().length) newPage = 0;
else if (newPage < 0) newPage = getPages().length;    // <-- should be length - 1
```

Then `GuiBook.drawCurrentPage` does `book.getPages()[currentPage]` (`GuiBook.java:164`) and
`onKeyboardInput`/`onMouseInput` do the same (`:223`, `:231`). Paging backwards from page 0 crashes.

---

## 2. P1 — Wrong results / silent data loss

### 2.1 `Book.globalData` is one static buffer shared by every book

```java
// books/api/Book.java:33-34
private static final String GLOBAL_DIR = "./CFBookData/";
private static volatile NBTTagCompound globalData;   // shared across ALL Book subclasses
```

`getData` (line 71) reads `GLOBAL_DIR + bookItem.getModId() + "/" + bookItem.getUnlocalizedName()`
into this one field, then `setData` (line 104) does `globalData = data` — so:

* Two different GLOBAL books overwrite each other's buffer.
* `globalData` is read from disk exactly once and never refreshed, so a second book's file is never
  loaded.
* `setData` captures `final NBTTagCompound tag = getData(stack)` on the calling thread and mutates it
  inside a worker thread with no synchronisation.
* `GLOBAL_DIR` is never `mkdir`'d, so `CompressedStreamTools.write` always fails
  (`NoSuchFileException`, swallowed at line 119).
* `player.getGameProfile()` (line 110) NPEs if `player` is null.

### 2.2 `LocalBookData` never persists

```java
// books/core/LocalBookData.java:62-65
public static void putLocalData(String key, NBTTagCompound tag) {
    loadData();
    data.put(key, tag);
}                                       // no markDirty()
```

`WorldSavedData.markDirty()` is never called, so LOCAL book data is written only if something else
happens to dirty the map storage. Also:

* `data` and `hasLoaded` are `static` — shared across worlds and dimensions, never cleared on world
  unload (leaks between saves in a single session).
* `createData()` (line 50) calls `setItemData` unconditionally on first access. `MapStorage.setData`
  throws `RuntimeException("Duplicate id: ...")` if the entry was already loaded, so ordering with
  `loadItemData` matters and is undocumented.
* `IDENTIFIER` is `public static` non-final (line 14).

### 2.3 Inventory NBT bloat

```java
// tiles/TileEntityInventory.java:55-61 (identical in entities/EntityInventory.java:44-50)
for (ItemStack item : items) {
    NBTTagCompound itemNbt = new NBTTagCompound();
    if (item != null) item.writeToNBT(itemNbt);
    itemsNbt.appendTag(itemNbt);        // empty compound per empty slot
}
```

A 64-slot TE writes 64 empty `TAG_Compound` entries on every chunk save, forever. Because
`readFromNBT` (`:46`) indexes positionally, removing the empties requires a format change — worth
doing now while the class is still unused. (Commit `a750319 "Fix inventory NPE on write NBT"` added
the `if (item != null)` guard, which is why the empties are there.)

### 2.4 `TileEntityInventory` calls an abstract method from its constructor

```java
// tiles/TileEntityInventory.java:25-27
public TileEntityInventory() {
    items = Arrays.asList(new ItemStack[getSize()]);   // getSize() is abstract
}
```

Classic virtual-call-in-constructor. Any subclass whose `getSize()` reads an instance field gets `0`
and a zero-length inventory. `readFromNBT` re-sizes later, so it only bites the freshly-placed path.

Also `items` is `Arrays.asList(...)` — a fixed-size list; `items.set` works but `add`/`remove` throw
`UnsupportedOperationException`. And `EntityInventory.getStackInSlot` (`:57`) has no bounds check
(unlike `getStackInSlotOnClosing`), so an out-of-range slot throws instead of returning null.

### 2.5 `EntityInventory` doesn't chain to `super`

`readEntityFromNBT` (`:33`) and `writeEntityToNBT` (`:43`) never call `super`, so Forge's
`Entity` NBT hooks (and any subclass state) are skipped.

### 2.6 `ReflectionUtils` object (de)serialisation cannot round-trip

```java
// ReflectionUtils.java:431 and :447
sb.append(getSignatureFromObject(o));    // `o` is the ENCLOSING object, not f.get(o)
```

Every field is tagged with the parent's type signature. Then:

```java
// :366
field.set(object, objectFromString(objectString, getTypeFromSignature(blockType).newInstance()));
```

* passes the **whole** `objectString` instead of the accumulated `block` variable,
* `getTypeFromSignature` can return `null` → NPE,
* `field` may be `null` → NPE,
* `getTypeFromSignature("[Ljava/lang/String;")` (`:171-177`) does `substring(1, length)` — keeps the
  trailing `;` → `ClassNotFoundException: java.lang.String;`.

Two `// FIXME: Don't use newInstance()` comments already flag part of this.

### 2.7 `ReflectionUtils.coerceStringToJavaObject` ignores the target type

```java
// ReflectionUtils.java:277-287
if (MathUtils.isStringNumber(s)) return s.contains(".") ? Double.parseDouble(s) : Integer.parseInt(s);
if (s.length() == 1) return s.charAt(0);
```

`objectFromString` then does `field.set(object, <coerced>)`. A `long` field receiving an `Integer`,
or a `char` field receiving an `Integer` (because `"5"` is numeric), throws
`IllegalArgumentException` — which is unchecked and uncaught.

### 2.8 `DefaultProxy` swallows type errors, then crashes the caller

```java
// config/DefaultProxy.java:84-90
public Object deserialize(String key, String s) {
    try { return deserializeUnSafe(key, s); }
    catch (ClassCastException | IllegalArgumentException e) { return s; }   // returns the raw String
}
```

`readFromReader` (`ConfigRegistry.java:452`) then does `f.set(config, <String>)` on an `int` field.
That `IllegalArgumentException` is **not** caught (only `ConfigException` is), so it escapes
`preInit`.

### 2.9 `DefaultProxy` array round-trip loses data

```java
// config/DefaultProxy.java:94-98
s = s.replaceFirst("\\[", "");
if (s.lastIndexOf("]") > -1) s = s.substring(0, s.lastIndexOf("]"));
String[] toSerialize = s.split(",");
```

* `[]` (empty array) → `""` → `split(",")` → `[""]` → a **1-element** array.
* Any `String[]` element containing a comma is split.
* `serialize` (`:69-81`) concatenates with `+=` in a loop (O(n²)) and does no escaping.

### 2.10 `Line.setEnd(ThreeDimensionalVector)` replaces the wrong endpoint

```java
// utils/math/geometry/Line.java:342-344
public Line setEnd(ThreeDimensionalVector coord) {
    return new Line(coord, end);        // <-- should be new Line(start, coord)
}
```

### 2.11 `ThreeDimensionalVector` math errors

```java
// :116-120  normalize()
: new ThreeDimensionalVector(x / distance, y / distance, y / distance);
//                                          ^ z should be z / distance

// :194-201  rotateX()
    Math.cos(rad) * (y - center.y) - Math.sin(rad) * (z - center.z) + center.z,
    Math.sin(rad) * (y - center.y) + Math.cos(rad) * (z - center.z) + center.z);
//  the new Y adds center.z; it must add center.y

// :220-227  rotateY()
    Math.cos(rad) * (z - center.z) - Math.sin(rad) * (x - center.x) + center.x,
    y,
    Math.sin(rad) * (z - center.z) + Math.cos(rad) * (x - center.x) + center.x);
//  the new Z must add center.z; also the rotation is about the wrong pair of axes
```

`rotateX` is only correct when `center.z == center.y`. `rotateY` is wrong for any non-origin centre.
`Location extends ThreeDimensionalVector`, so this affects every world-position rotation.

### 2.12 `Variable2DShape` geometry

* `getCentroid` (`:156-157`) divides by `getNumberOfSides()` instead of `vertices.length` — wrong for
  any shape where the two differ (they don't for a simple polygon, but the method is used as a
  general centroid).
* `getAngleForVertex` (`:129-131`) uses `Math.atan(|(m1-m2)/(1+m1*m2)|)`. `atan` has no quadrant, so
  obtuse interior angles come back acute; vertical edges are faked with `Double.MAX_VALUE`, which
  makes `1 + m1*m2` overflow to `Infinity` and the result `0`.
* The varargs constructor (`:24-32`) calls `Arrays.sort(vectors)`, mutating the **caller's** array.

### 2.13 `Circle` / `Line` floating-point equality

```java
// geometry/Circle.java:113-116
return Math.sqrt(...) == radius;          // exact double comparison -> effectively always false

// geometry/Line.java:167
(find2DPoint(coord.getX()).getY() == coord.getY());
// :238
(find3DPoint(coord.getX()).getY() == coord.getY() && ...)
```

`Circle.isPointInCircle` (`:124-126`) is built on `isPointOnCircumference`, so it degenerates to
`distance < radius` — a point exactly on the boundary is reported as outside. `Line.getAll2DPoints`
and `Variable2DShape.findSidesWithPoint` are built on `isPointValid`, so they return almost nothing.

### 2.14 `Location.hashCode()` returns 0

```java
// minecraft/utils/Location.java:134-137
@Override
public int hashCode() { // Done to allow for use as keys in HashMaps
    return 0;
}
```

Every `Location` lands in one bucket. `StructureCreator` (`:12-14`) uses three `HashMap<Location,…>`,
so `isStructureValid()`/`generateStructure()` are O(n²) over the structure's block count. The comment
is also backwards: returning a constant is what *breaks* hash-based lookup; it does not enable it.

Also `Location.equals` (`:126-129`) NPEs if either `world` is `null` (the private
`Location(double,double,double)` constructor at `:25` leaves it null).

### 2.15 `StructureCreator` stores metadata keyed by block

```java
// minecraft/utils/StructureCreator.java:13
private HashMap<Block, Integer> metas = new HashMap<Block, Integer>();
```

Placing `Blocks.log` with meta 0 and again with meta 3 in the same structure: the second
`addBlock` overwrites `metas.put(block, meta)`, so both locations generate with meta 3. The map
should be keyed by `Location`.

### 2.16 `LogicUtils.xor` is not parity

```java
// utils/LogicUtils.java:79-91
for (boolean bool : bools) {
    if (total && bool) { if (didOr) return false; }
    else { total = total || bool; didOr = true; }
}
```

`xor(true, true, true)` returns `false`; the correct parity is `true`. It is correct only for 0, 1
and 2 inputs. `xnor` inherits the bug.

### 2.17 `NBTHelper` getters mutate the stack

Every getter (`getLong`, `getString`, `getBoolean`, `getByte`, `getShort`, `getInt`, `getFloat`,
`getDouble`, `getList`, `getTag`, `getIntArray`, `getCompoundTag`) calls `initNBTTagCompound` and,
if the key is absent, writes a default value. Reading NBT therefore creates NBT and dirties the
item — surprising, and it makes `hasTag` unreliable as a "was this ever set" test.

### 2.18 `GuiBook` leaks itself on the event bus and NPEs afterwards

```java
// books/core/GuiBook.java:44-67
public GuiBook(EntityPlayer player) {
    MinecraftForge.EVENT_BUS.register(this);        // registered FIRST
    ItemStack heldItem = player.getHeldItem();
    if (heldItem == null || !(heldItem.getItem() instanceof ItemBook)) {
        GuiUtils.closeCurrentGui();
        return;                                     // <-- never unregistered, book == null
    }
    ...
}
```

If the constructor bails, the half-built object stays registered; `onMouseInput` (`:231`) and
`initGui` (`:86`) then NPE on `book`. `onGuiClosed` (`:261`) only unregisters if it gets that far.

### 2.19 `GuiBook` mouse hit-test and argument order are both wrong

```java
// books/core/GuiBook.java:246-256
if (event.x >= startCoords.getRoundedX() && event.x <= startCoords.getRoundedX() + entry.width
    && event.y >= startCoords.getRoundedY()
    && event.y <= entry.height)                       // <-- missing startCoords.getRoundedY() +
    ((InteractiveEntry) entry).onMouseEvent(
        event.x - offsetX, event.y - offsetY,
        event.dx, event.dy,
        event.button,                                  // <-- passed as `dWheel`
        event.dwheel,                                  // <-- passed as `button`
        event.buttonstate);
```

The interface is `onMouseEvent(int x, int y, int dx, int dy, int dWheel, int button, boolean
buttonState)` (`books/api/InteractiveEntry.java:40`) — `button` and `dWheel` are swapped. The Y
bound is also wrong: it compares against `entry.height` rather than `startCoords.getRoundedY() +
entry.height`, so entries below `height` px are unclickable and entries at the top are over-wide.

### 2.20 `GuiUtils.closeCurrentGui` bypasses `displayGuiScreen`

```java
// minecraft/utils/client/GuiUtils.java:34-37, 46-50
Minecraft.getMinecraft().currentScreen.onGuiClosed();
Minecraft.getMinecraft().currentScreen = null;
```

Setting `currentScreen` directly skips `Minecraft.displayGuiScreen(null)`, which is what
`setIngameFocus()`/`ungrabMouseCursor()` are reached through. The result is a stuck grabbed cursor
or a world that never resumes. Use `mc.displayGuiScreen(null)`.

---

## 3. P2 — Robustness, edge cases, API smells

### 3.1 Network handlers run on the netty thread

In 1.7.10, `SimpleNetworkWrapper` invokes `IMessageHandler.onMessage` on the netty event loop. None
of the four handlers schedule onto the main thread, yet all of them touch world state:

* `ConfigPacket.ConfigPacketHandler` (`:51`) mutates **static config fields** and performs file I/O.
* `TileEntityClientUpdatePacket` / `TileEntityServerUpdatePacket` handlers (`:60`) read/write tile
  entities.
* `TimeUpdatePacket` (`:56`) sets world time on every `WorldServer`.

`TileEntityClientUpdatePacket` additionally touches a client world from the netty thread. All four
need a main-thread hop.

### 3.2 `fromBytes` is not defensive

`ConfigPacket.fromBytes` (`:33`), `TileEntity*UpdatePacket.fromBytes` (`:37`),
`TimeUpdatePacket.fromBytes` (`:34`) all assume `ByteBufUtils.readTag(buf)` returns non-null. An
empty/truncated payload NPEs on the netty thread. `TimeUpdatePacket` also assumes the UUID string is
well formed.

### 3.3 `CurseVersionChecker` NPEs on any network failure

`doCheckIfNescessary` (`:35-46`) catches the exception and leaves `check == null`; `isUpdateAvailable`
(`:55`), `getDownloadUrl` (`:65`), `getUpdateFileName` (`:75`) and `getProjectUrl` (`:85`) then
dereference it. `didCheck` (`:23`) is written nowhere and read nowhere. The
`widget.mcf.li` API has been dead for years.

### 3.4 HTTP clients are unsafe

| File | Issues |
|---|---|
| `utils/WebUtils.java:37-49` | `connect()` called *after* `getInputStream()`; connect timeout only, no read timeout; no charset; stream not closed on exception |
| `utils/WebUtils.java:51-68` | same, plus `(HttpURLConnection)` cast can `ClassCastException` |
| `services/GithubService.java:38-52, 97-112, 129-144` | same; **no `User-Agent` header → GitHub returns 403**; returns `null` on error, so callers NPE |
| `dependencies/download/BinaryProvider.java:16-48` | `header.get(null)` NPEs when the null key is absent (`:44`); unbounded redirect loop; no timeouts; `input`/`output` not closed on exception; status code ignored |
| `dependencies/download/PlainTextProvider.java:15-27` | `FileWriter` with the default charset; parent directory not created |

### 3.5 `DependencyManager.loadLibrary` is a no-op

```java
// dependencies/DependencyManager.java:50-55
public static void loadLibrary(File file) throws MalformedURLException {
    URLClassLoader loader = new URLClassLoader(new URL[] { file.toURI().toURL() },
                                               DependencyManager.class.getClassLoader());
}   // loader discarded, never registered, never closed
```

It compiles, does nothing, and leaks a `URLClassLoader` (file handle). `MinecraftDependencyManager`
overrides it correctly — the base implementation should be abstract or throw
`UnsupportedOperationException`.

### 3.6 Provider selection picks the *last* match

```java
// dependencies/DependencyManager.java:69-73 (and :90-94)
for (IDownloadProvider p : providers)
    for (FileType t : p.getCapabilities())
        if (t.toString().equals(fileToDownload.toString())) { provider = p; break; }   // inner break only
```

The documented contract is "the earlier the registration, the higher priority". The outer loop keeps
running, so the **last** matching provider wins. Needs a labelled break or a `return`.

### 3.7 `DownloadFuture` doesn't implement `Future`

```java
// dependencies/DependencyManager.java:127-135
public Boolean get() { return runnable.didDownload; }                          // no wait
public Boolean get(long timeout, TimeUnit unit) { return runnable.didDownload; } // ignores timeout
```

`get()` returns `false` if the download hasn't started yet. `isDone()` is `runnable.isDone`, which is
only ever set at the end of `run()`. Also `DownloadRunnable.getName()` (`:161`) uses
`numOfDownloaders` (a monotonically increasing static counter) instead of the instance's
`downloadNum`, so all download threads collide on the same name — and `SimpleThread.equals`
compares by name (`SimpleThread.java:108-110`).

### 3.8 `FileType.getFileTypeForOther` mutates a shared enum constant

```java
// dependencies/download/FileType.java:20-24
public static FileType getFileTypeForOther(String otherType) {
    FileType t = FileType.OTHER;
    t.type = otherType;      // global mutable state on a singleton
    return t;
}
```

Two concurrent calls corrupt each other; `toString()` also returns `null` for a fresh `OTHER`, which
`NoProviderFoundException` stringifies as `"null"`.

### 3.9 `FileUtils` issues

* `readAll` (`:46`) uses `while (reader.ready())` — `ready()` can return `false` before EOF, truncating.
* `unzip` (`:58-77`) has **no zip-slip guard** (`new File(toDirectory.getPath() + "/" + zip.getName())`
  will happily escape `toDirectory` via `../`); `read(buffer) > 0` should be `!= -1`.
* `parseClassesFromJar` (`:87-105`) has an empty `catch` (`:100`), never closes the `JarInputStream`,
  and `Class.forName` cannot load classes that are not on the classpath — it always returns an empty
  list. Effectively dead.

### 3.10 `MinecraftTranslator`

* `mcLangCodesToUsable` (`:106-110`): `code.split("_")` then `langInfo[1]` → `AIOOBE` for any code
  without `_`. The whole method is equivalent to `code.split("_")[0]` — the `Locale` is pointless.
* `onTooltipEvent` (`:120`) calls `event.itemStack.setStackDisplayName(...)`. Hovering an item
  **permanently rewrites its NBT**, and fires a blocking HTTP request per hover on the render thread.
* `onChatEvent` (`:134`) replaces the component with a plain `ChatComponentText`, discarding
  formatting, click and hover events.
* `getFallback` (`:140-146`) reflects into `StatCollector.fallbackTranslator`. The project already
  ships an access transformer (`cf_at.cfg`) — using an AT here would be consistent and cheaper than
  reflection per call.
* `MinecraftTranslator` is instantiated once in `ClientProxy` and its statics are called from
  anywhere — mixed static/instance API.

### 3.11 `YandexProvider`

* `translate` (`:29`): `text.replace(" ", "+")` with no `URLEncoder` — breaks on `&`, `#`, `%`, `+`,
  and all non-ASCII.
* `detectLangauge` (`:62-71`) **ignores its `text` argument** and always sends `text=Hello+world`.
* `response` can be `null` after a failed `gson.fromJson` → NPE at `response.code`.

### 3.12 `TranslationManager` recursion

`catch (QueryLimitException) { unusableProviders.add(...); return translate(...); }` (`:66-69`,
`:99-102`, `:126-129`) recurses without a depth limit. Once every provider is in `unusableProviders`,
the loop at `:52` never finds one and either throws (bug 1.8) or `IndexOutOfBoundsException`s.

### 3.13 `ArrayUtils`

* `wrappedRetrieve` (all 9 overloads, e.g. `:280-284`): a negative index reaches `array[index]`
  → `AIOOBE`; a zero-length array makes `while (index >= array.length)` an **infinite loop**.
* `indexOf(Object[] array, Object key)` (`:19-24`) NPEs on a null element, and ignores
  `Arrays.asList(...).indexOf`.
* `removeRepeats` (all 9 overloads) uses `List.contains` in a loop → O(n²). A `LinkedHashSet` is
  O(n) and preserves order.
* `removeRepeats(T[])` (`:396-400`) does `cache.toArray(Arrays.copyOf(array, cache.size()))` — works,
  but only by luck of the copy length.

### 3.14 `MathUtils`

* `getMin`/`getMax` (`:29-45`) throw `AIOOBE` on empty input.
* `findLowestCommonDenominator` (`:94-102`) is misnamed **and** wrong for its caller: it returns the
  *smallest* common divisor at or above 2, not the greatest one, and it starts the search at 2 so
  coprime values return `NaN` instead of 1. `Ratio` divides by whatever it returns, so
  `new Ratio(1920, 1080)` reduces to **960:540**, not 16:9. It is also capped at
  `MAX_PASSES = 1024`, so a 4096×2048 texture (divisor 2048) is never reduced at all.
  Confirmed by `MathUtilsTest` / `RatioTest`.
* `Ratio` (`:22`) does `reducedValues = originalValues` on the NaN path, **aliasing** the caller's
  array; `getOriginalRatio`/`getReducedRatio` hand out the internal arrays.
* `isWholeNumber` (`:19-21`) compares `intValue() == longValue()`, which is `true` for *any* value
  that fits in an `int` — including fractions. `isWholeNumber(3.5)` returns `true`.
  Confirmed by `MathUtilsTest`.
* `MAX_PASSES` is `public static` non-final.

### 3.15 `equals` without `hashCode`

Six classes override `equals` and not `hashCode`:

```
multithreading/SimpleRunnable.java
multithreading/SimpleThread.java
utils/math/ThreeDimensionalVector.java
utils/math/TwoDimensionalVector.java
utils/math/geometry/Line.java
utils/math/geometry/Variable2DShape.java
```

`TwoDimensionalVector` and `ThreeDimensionalVector` are used as `HashMap`/`HashSet` keys
(`Variable2DShape`, `ArrayUtils.removeRepeats`) — identity hashing with value equality is a
correctness bug, not just a smell.

### 3.16 `ItemBase` / `BlockBase`

* `ItemBase.getUnlocalizedName(ItemStack)` (`:45-50`) ignores the `itemStack` parameter entirely.
* `getModId().toLowerCase()` (`:40`, `:48`; `BlockBase.java:41`) — no `Locale`, and NPEs if a
  subclass returns `null`.
* `BlockBase.getUnlocalizedName()` (`:42`) double-unwraps:
  `getUnwrappedUnlocalizedName(getUnwrappedUnlocalizedName(super.getUnlocalizedName()))`. It happens
  to work because the second unwrap's `indexOf(".") == -1` → `substring(0)`, but it silently breaks
  for any name containing a `.`.
* `ExclusiveSlot.isItemValid` (`:47-48`) NPEs if `itemStack` is null.

### 3.17 `Modules` semantics

```java
// compat/modules/Modules.java:28-31
private static boolean canModuleLoad(IModule module) {
    for (String s : module.modsCompatible()) if (Loader.isModLoaded(s)) return true;
    return false;
}
```

A module that declares **no** requirements (`modsCompatible()` returning `{}`) never loads. The
interface Javadoc says "not strict", implying an empty list should mean "always". Also
`propagate` (`:51-56`) casts every non-pre/init event to `FMLPostInitializationEvent`, so passing
`FMLServerStartingEvent` is a `ClassCastException` waiting to happen.

### 3.18 `ConfigRegistry.ConfigProxy` field discovery

```java
// ConfigRegistry.java:486-509
Field[] declared = config.getClass().getDeclaredFields();
for (Field f : declared) { f.setAccessible(true); if (exclude.indexOf(f.getName()) == -1) ... }
```

* Includes **synthetic** fields: `this$0` for non-static inner classes, `$assertionsDisabled`, and
  any lambda-captured fields. These become config entries.
  **Empirically confirmed:** with JaCoCo instrumentation enabled (see §10), the synthetic
  `$jacocoData` field was written into the config file as
  `Boolean Array:$jacocoData=[true,true]` in a spurious `General` category. Any coverage-instrumented
  build corrupts every user's config file this way.
* No `static` filter — an arbitrary `public static` helper object in a config class becomes an
  entry (and then `serialize` throws `ConfigException`).
* `getDeclaredFields` + `getFields` misses `private` superclass fields.
* `addToCategory` (`:512-518`) silently overwrites a duplicate field name.
* `implements Cloneable` (`:466`) with no `clone()` override.
* `ArrayUtils.indexOf(annotation.exclude(), f.getName())` is O(n) per field per registration.

### 3.19 Config init is run three times

```java
// CollectiveFramework.java:58, :70, :80
ConfigRegistry.init();
```

`init()` drains `toBeInitialized` and clears it, so calls 2 and 3 are no-ops — but they are
misleading, and the `configs.add(configProxy)` in `initialize` (`:94`) would duplicate entries if a
config were ever registered after the first `init()`.

### 3.20 `ConfigRegistry` public mutable statics

```java
// ConfigRegistry.java:39-42
private static List<ConfigProxy> toBeInitialized = new ArrayList<ConfigProxy>();
public static List<ConfigProxy> configs = new ArrayList<ConfigProxy>();
private static List<IConfigProxy> proxies = new ArrayList<IConfigProxy>();
```

`configs` is public, mutable, and iterated unsynchronised from `onServerJoin`, the GUI, and the
netty thread. Same for `CollectiveFramework.LOGGER` (`:42`, non-final public static) and
`LocalBookData.IDENTIFIER` (`:14`).

### 3.21 `GhostEntityPlayerMP`

* `MinecraftServer.getServer()` (`:20`) NPEs on a client.
* `new GameProfile(null, name)` — a null UUID is invalid for most consumers.
* Constructed fresh for **every** `/say` command (`CommandBroadcastHandler.java:25`), including a
  full `EntityPlayerMP`/`ItemInWorldManager` allocation that is immediately discarded.
* `getPlayerForSender` (`:42`) casts `WorldUtils.getPlayerForWorld(...)`'s `EntityPlayer` to
  `EntityPlayerMP` — safe only because this runs server-side.

### 3.22 `CommandBroadcastHandler` truncates multi-word `/say`

```java
// event/handler/CommandBroadcastHandler.java:26
event.parameters[0]
```

`/say hello world` relays only `hello` in the `ServerChatEvent`. The parsed component
(`component`) is used for the *display* text, so the two disagree. Also the relay fires before
knowing whether the command succeeds.

### 3.23 `Entry extends GuiScreen`

`books/api/Entry.java:15` makes every text box a full `GuiScreen`, and its constructor (`:41-42`)
calls `Minecraft.getMinecraft()`. `Entry` is not `@SideOnly(CLIENT)`, so instantiating any entry on a
dedicated server NPEs. `TextEntry` (`:34-35`) does the same in a field initialiser, which runs before
any constructor guard.

### 3.24 `TextEntry` rendering

* `fitString` (`:47-62`) pushes `""` into the list when the first word already exceeds `width`, and
  prepends a space (`currentString + " " + s` when `currentString` is empty).
* The JUSTIFY branch (`:100-117`) does `spacesToAdd += words.length` then a nested `while`/`for` that
  only terminates because `spacesToAdd` decrements; a negative `spacesToAdd` silently left-aligns.
* `renderer.setUnicodeFlag(unicode)` is not restored if `drawString` throws.
* `renderer` is captured once and never refreshed after an F3+T resource reload.

### 3.25 `ImageEntry` scaling origin

`GuiBook.drawEntries` (`:96-109`) applies `glTranslated(backX, backY, 0)` and then
`ImageEntry.onRender` (`:49-76`) does `glScaled(...)` **after** that translate — so the scale pivots
on the book origin, not the entry origin. Scaled images land in the wrong place.

### 3.26 `KeyOverlay`

* `KEY_LIFE_LENGTH / (ticksLived * 2)` (`:32`) — an `int` division that yields `0` for most values
  (90/180 = 0), and the fade is inverted (newer keys are bright, older keys go *darker* toward
  black rather than fading out).
* `ConcurrentLinkedDeque<Object[]>` (`:18`) — untyped, unvalidated, and `keys.pop()` inside
  `while (!keys.isEmpty())` (`:27-28`) is a TOCTOU race against `KeyHandler`'s `offer`.
* `renderOverlay` is also registered on the Forge bus (correct) but gated on a config option whose
  producer never runs (see 1.1).

### 3.27 `Logger` wrapper

`log(Level, Object)` (`:23-25`) calls the deprecated
`FMLLog.log(String, Level, String)` with a formatted string instead of a parameterised message, so
every log site loses its stack trace / formatting. `all` (`:32`) and `off` (`:77`) are not meaningful
levels to log *at*. The class name shadows `org.apache.logging.log4j.Logger`, which is imported in
the same file.

### 3.28 `Config.yandexApiKey` / stale config surface

Beyond 1.18: `Config` has no `Misc.applyColorPatch`, `Misc.clickOnMainMenuOpen`,
`Misc.disableRenderTextEvents` or `Misc.enableCloudToButt` anymore, but the lang file still defines
them (see §5).

### 3.29 `ReflectionUtils.paramsMatch`

```java
// ReflectionUtils.java:83-91
for (int i = 0; i < parameters.length; i++)
    if (!parameters[i].getName().equals(params[i].getName())) return false;
```

Compares `Class.getName()` strings instead of the `Class` objects. Works, but `parameters[i] !=
params[i]` is both correct and faster, and doesn't conflate two classes with the same binary name
loaded by different classloaders (a real scenario in Forge).

### 3.30 `ReflectionUtils.isNamePrimitive` is a copy of `isNamePrimitiveOrPrimitiveObject`

```java
// :221-232 and :240-251
```

Character-for-character identical, despite the Javadoc claiming one excludes wrapper names. And
neither recognises `Integer`/`Character` in their *object* form consistently — `isNamePrimitive("Integer")`
returns `true`, contradicting its own doc.

---

## 4. Dead code inventory

### 4.1 Unreachable / never-called

| Item | Evidence |
|---|---|
| `compat/modules/Modules` + `IModule` | `registerModCompat` has **zero** call sites; `Modules.init()` filters an empty list |
| `DependencyManager.loadLibrary` | creates and discards a `URLClassLoader` |
| `CurseVersionChecker.didCheck` | declared, never written, never read |
| `CurseVersionChecker` (whole class) | no internal consumers; `widget.mcf.li` API is defunct |
| `GithubService` (whole class) | no internal consumers |
| `Location.toChunkCoordinates()` | duplicate of `chunkCoordsFromLocation()` (`Location.java:85` vs `:119`); neither is called |
| `ReflectionUtils.getSignatureFromObject(Object, boolean)` | `isRecursive` parameter is unused |
| `ReflectionUtils.isNamePrimitive` | identical to `isNamePrimitiveOrPrimitiveObject` |
| `MathUtils.isWholeNumber`, `isEvenNumber` | zero call sites |
| `ReflectionUtils.classImplementsInterface` | zero call sites |
| `ReflectionUtils.objectToString` / `objectFromString` / `coerceStringToArray` | only reachable from each other |
| `FileUtils.safeWrite`, `readAll`, `parseClassesFromJar` | zero call sites |
| `WebUtils.readGithub` (both overloads) | `@Deprecated`, zero call sites |
| `SimpleRunnable.setTicking` | zero call sites |
| `Location.getPlayers` | zero call sites |
| `Logger.all`, `Logger.off` | nonsense levels |
| `ConfigRegistry.ConfigProxy implements Cloneable` | no `clone()` |
| `Entry.setPitch` / `setYaw` / `setRotation` | never called; `pitch`/`yaw`/`rotation` stay 0 |
| `Book.drawDebugLines` / `Entry.drawDebugLines` / `useAbsoluteCoords` | never set to `true` anywhere |

### 4.2 Dead resources

* `src/main/resources/rehost/javazoom/jl/decoder/{au2lin,l3reorder,lin2au,sfd}.ser` — jlayer was
  removed in commit `2b5f315 "Removed javazoom jlayer"`. Nothing in `src/` references `javazoom`,
  `rehost` or `jl/decoder`. Delete all four.
* `src/main/resources/META-INF/cf_at.cfg` — the only entry
  (`RenderGlobal.field_72773_u` / `cloudTickCounter`) is only used by
  `ClientTickHandler.java:31`, which never runs (§1.1). Either fix the handler or drop the AT (and
  the `accessTransformersFile` property).

### 4.3 Dead lang keys

`assets/theframework/lang/en_US.lang` defines options that no longer exist in
`reference/Config.java`:

```
theframework.lang.config.Misc.applyColorPatch
theframework.lang.config.Misc.clickOnMainMenuOpen
theframework.lang.config.Misc.disableRenderTextEvents
theframework.lang.config.Misc.enableCloudToButt
```

### 4.4 Public API with no internal consumer (intentional, but undocumented)

`StructureCreator`, `WorldGenHelper`, `ExclusiveSlot`, `NetworkedTileEntity`, `EntityInventory`,
`ItemBase`, `BlockBase`, `ImageEntry`, `TextEntry`, `NBTHelper`, `Colors`, `IconManager`,
`ModIds` (27 constants, none used), `MinecraftDependencyManager`, `Encryption`/`SecretValue`,
the whole `utils/math` + `utils/math/geometry` tree.

`ModIds` is worth calling out: 27 public constants for mods that (for the most part) are not on the
GTNH 1.7.10 modlist, none of them referenced anywhere.

---

## 5. Performance

Ranked by impact.

1. **`GuiBook` reads a PNG from disk every frame, per entry.**
   `drawEntries` (`GuiBook.java:100`), `drawCurrentPage` (`:168`), `drawBackground` (`:188`) and
   `onMouseInput` (`:237`) all call `ImageIO.read(GuiUtils.getResourceAsStream(...))`. For a book
   with 5 entries that is 6 PNG decodes per frame at 60 fps ≈ **360 decodes/second**. Cache
   `(width, height)` per `ResourceLocation` in a `Map`.
2. **`SimpleThread` spin loops** (§1.13) — one pegged core per disabled/queued `SimpleRunnable`.
3. **`HeavyCalculations.FutureImpl.get()`** (`:215`) — `while (!isDone) {}`.
4. **`Location.hashCode() == 0`** (§2.14) — every `StructureCreator` map becomes a linked list.
5. **`GuiUtils.getCurrentResolution()`** (`:72-77`) allocates a `ScaledResolution` per call; called
   2–4 times per frame from `GuiBook`.
6. **String building with `+=` in loops** — `StringUtils.repeatChar`/`repeatString`/`stringFromList`/
   `stringFromArray` (`:21-63`), `DefaultProxy.serialize` (`:69-81`), `LogicUtils` n/a,
   `Book`/`GithubService` changelog. All O(n²); use `StringBuilder`.
7. **`ArrayUtils.removeRepeats`** — `List.contains` in a loop, O(n²) × 9 overloads.
8. **`TranslationManager.translationCache`** — unbounded `HashMap`, never evicted (§1.9).
9. **`Book.setData(GLOBAL)`** (`:105-128`) — spawns a **new OS thread per save** via
   `new SimpleRunnable(){...}.start()`, and that thread then spins forever.
10. **Blocking HTTP on the render/network thread** — `MinecraftTranslator.onTooltipEvent` (`:120`,
    render thread), `onChatEvent` (`:134`, netty thread), `CurseVersionChecker` (whatever calls it).
11. **`WorldUtils.getNearestEntityToLocation`** (`:48-63`) allocates a `Vec3` per loaded entity per
    call.
12. **`ReflectionUtils.getDeclaredOrNormalField`** — linear scan on every config read, parse and GUI
    open. Cache `Map<String, Field>` per class.
13. **`ConfigRegistry.DefaultConfigurationHandler.getCategories`** — `field.setAccessible(true)` and
    a full re-serialise per field per GUI open; fine, but `serialize` is O(n²) for arrays.
14. **`GhostEntityPlayerMP`** — full `EntityPlayerMP` + `ItemInWorldManager` allocation per `/say`.

---

## 6. Refactors

### 6.1 Split `ConfigRegistry` (547 lines)

It currently holds the static registry, the `ConfigProxy`, the default INI handler, and the GUI
element builder. Split into `ConfigRegistry`, `ConfigProxy`, `DefaultConfigurationHandler`,
`ConfigGuiBuilder`.

### 6.2 Replace the hand-rolled INI format

`writeToStream`/`readFromReader` are ~80 lines of string slicing with no validation. Forge's
`Configuration`/`Property`/`ConfigCategory` are already on the classpath (`getForgeConfigType` and
`getCategories` already use them) and give you type validation, comments, category ordering and a
working GUI for free. Alternatively Gson + a `Config` POJO. Either removes §1.5, §1.6, §2.8, §2.9
and §3.18 at once.

### 6.3 Replace `SimpleRunnable`/`SimpleThread`/`HeavyCalculations`

All three should be built on `java.util.concurrent`:

* `SimpleRunnable` → `ScheduledExecutorService` (or `ThreadPoolExecutor` + a delay queue).
* `SimpleThread` → delete entirely.
* `HeavyCalculations` → `ThreadPoolExecutor` returning real `Future`s. It is ~230 lines reimplementing
  something the JDK does correctly.

If the "restrict thread usage" config is genuinely wanted, express it as a bounded
`ThreadPoolExecutor` size, not as a hand-rolled pool of `Thread` subclasses.

### 6.4 `Entry` should not be a `GuiScreen`

`Entry` is a data + render pair; `GuiScreen` brings a whole window lifecycle, an `mc` field, and
client-only initialisation into a class that is documented as a "text box". Make it a POJO with an
`onRender(int dt, FontRenderer)` signature and let `GuiBook` own the GL state.

### 6.5 Collapse the two tile-entity packets

`TileEntityClientUpdatePacket` and `TileEntityServerUpdatePacket` are byte-for-byte identical except
for the class name and the `IMessageHandler` target. One packet with an explicit direction field
(or just one bidirectional `NBTSyncPacket`) removes ~60 lines and one class of "which one do I
register" bugs.

### 6.6 `Book` storage strategies

The `if (GLOBAL) … else if (LOCAL) … else …` chain appears twice (`getData:71-92`,
`setData:101-145`) with divergent bugs in each branch. Extract
`interface BookStorage { NBTTagCompound read(); void write(NBTTagCompound); }` with three
implementations.

### 6.7 `Inventory` helper

`TileEntityInventory` and `EntityInventory` duplicate ~80 lines of `IInventory` boilerplate.
Extract an `InventoryHelper` (or an abstract base) and use a real `ItemStack[]` /
`NonNullList<ItemStack>` instead of `Arrays.asList`.

Also, `TileEntityInventory` ships a `removeStackFromSlotStatic` helper (`:103-113`) but never wires it
into `IInventory#getStackInSlotOnClosing`, so **every** subclass has to implement that method itself
(discovered while writing the in-game test). Wire it up, or make the class non-abstract for that
method.

### 6.8 `Logger`

Wrap log4j properly:

```java
private final org.apache.logging.log4j.Logger log = LogManager.getLogger(Reference.MOD_ID);
public void warn(String msg, Object... args) { log.warn(msg, args); }
public void debug(String msg, Throwable t) { log.debug(msg, t); }
```

`FMLLog.log(String, Level, String)` is deprecated and stringifies everything, so
`LOGGER.debug(e)` (`ConfigRegistry.java:368`) logs an exception as its `toString()` — no stack trace.

### 6.9 Nullability

The codebase is inconsistent: `TileEntityInventory.getStackInSlot` has `@Nullable` (`:70`) but the
`TileEntityClientUpdatePacket` handler (`:61`) assumes non-null. Adopt `javax.annotation.Nullable` /
`@Nonnull` consistently (the `javax.annotation` import is already present in `TileEntityInventory`
and `GuiFactory`) and enable a checker, or use `Optional` at the API boundary.

### 6.10 `Locale`-aware string ops

`getModId().toLowerCase()` (`ItemBase.java:40,48`; `BlockBase.java:41`),
`Property.Type.tryParse(o.getClass().getSimpleName().toUpperCase(...))` (`ConfigRegistry.java:185-187`)
— the latter already uses `Locale.US`, the former doesn't.

### 6.11 `Colors`

`public String code` (`:33`) should be `final`. `COLOR_CHARS` (`:35`) duplicates the enum and will
drift; derive it once in a static block.

### 6.12 `ArrayUtils` / `StringUtils` / `LogicUtils` / `MathUtils`

These are 1,100 lines of hand-rolled `java.util` / Guava / commons-lang equivalents. Guava is
already a transitive dependency (it's used by FML). At minimum, delegate
`removeRepeats` → `LinkedHashSet`, `indexOf` → `Arrays.asList().indexOf`,
`repeatString` → `Strings.repeat`, `isBetween` → `Range`.

---

## 7. Feature suggestions

### 7.1 Config
* Move to a real backend (see 6.2) and add `@Range(min, max)` / `@AllowedValues` validation.
* Add a field-level `@Ignore` annotation instead of the string `exclude` list — string lists drift
  silently when fields are renamed.
* Wire the sync properly (§1.1) and add a `ConfigSyncEvent` so mods can veto per-field.
* Only sync non-`clientSideOnly` fields explicitly rather than relying on `convertToString`'s side
  check (`ConfigRegistry.java:390-391`) — right now the behaviour is correct only by accident.
* Add `/cf reload <config>` so servers can reload without a restart.

### 7.2 Tests

There is **no** `src/test`. The majority of the bugs in §2 are pure-Java and testable in
milliseconds without Minecraft:

* `DefaultProxy` serialize/deserialize round-trips (including empty arrays, comma-containing
  strings, `char`, all primitives).
* `ConfigRegistry` write→read→write idempotency, including a `clientSideOnly` field in last position.
* `ArrayUtils`, `StringUtils`, `LogicUtils.xor` (3+ inputs), `MathUtils`.
* `TwoDimensionalVector`/`ThreeDimensionalVector` rotate/normalize.
* `Encryption` encrypt→decrypt round-trip.
* `TranslationManager` provider fallback with a fake `ITranslationProvider`.

Add `junit` + `testImplementation` in `dependencies.gradle` and a `test` job in CI.

### 7.3 Lifecycle
* Add `FMLServerStoppingEvent`/`FMLServerStoppedEvent` handlers to shut down `HeavyCalculations`
  and any executor, and to clear `TranslationManager`'s cache.
* Add `WorldEvent.Unload` to clear `LocalBookData`'s static map.

### 7.4 Networking
* Add a `TileEntitySyncRegistry` so TEs opt into syncing via a small interface, with distance and
  ownership validation in one place.
* Add a `PacketRateLimiter` (token bucket per player) — currently `TimeUpdatePacket` and
  `TileEntityServerUpdatePacket` are unbounded.

### 7.5 Books
* Add a `BookRegistry` so `ItemBook` subclasses can be resolved by id instead of by reflective
  constructor lookup (`GuiBook.java:54-56`).
* Cache decoded image dimensions (§5.1).
* Add proper pagination (`nextPage()`/`prevPage()`) so the off-by-one at `Book.java:163` can't recur.

### 7.6 Translation
* LRU cache with a TTL, keyed by `(text, fromLang, toLang)`.
* Make the provider key user-configurable (finally use `Config.yandexApiKey`), and move the
  credential out of source.
* Run translation off the render/network thread and apply the result on the next tick.
* Preserve `IChatComponent` formatting by translating per-`ChatStyle` run rather than replacing the
  whole component.

### 7.7 `Modules`
Either register at least one module (a `NotEnoughItems` or `WAILA` integration would justify the
class existing) or delete `Modules`/`IModule` — 97 lines of unused infrastructure.

### 7.8 `CurseVersionChecker`
Replace with the Modrinth v2 or CurseForge v1 API, run it off-thread, cache the result, and handle
failure by returning "unknown" instead of NPE.

---

## 8. Build / packaging / repository hygiene

1. **`README.md` is badly stale.** Travis CI badges (no Travis config exists), Bintray maven
   instructions (Bintray was shut down in 2021), "The Framework" naming, and
   `com.rolandoislas.theframework` coordinates that no longer match `modGroup`. Rewrite.
2. **`modName` / asset-domain / `MOD_NAME` are three different names**: `gradle.properties`
   `modName = CollectiveFramework`, `Reference.MOD_NAME = "CollectiveFramework"`, asset domain
   `theframework`, README title "The Framework". Pick one.
3. **`gradle.properties`:**
   * `autoUpdateBuildScript = true` — the buildscript rewrites itself; unreviewed changes land in
     PRs and CI.
   * `usesMavenPublishing = true` but `modrinthProjectId` and `curseForgeProjectId` are empty →
     publishing silently no-ops.
   * `useModGroupForPublishing = true` with `modGroup = com.austinv11.collectiveframework` →
     publishes under the *original author's* group. Set `com.github.MrFuzzihead` or flip the flag.
   * `.java-version` says `25`, `enableModernJavaSyntax = jabel` targets Java 8 bytecode — pin the
     toolchain explicitly (`forceToolchainVersion = 8`) or the local and CI JDKs will diverge.
4. **`dependencies.gradle` and `repositories.gradle` are empty boilerplate** (50 lines of comments,
   0 lines of config). Either fill them in (you need `gson`? it's transitive via MC — but that's
   fragile; declare it) or delete them.
5. **`.gitignore` ignores `*.bat` then un-ignores `gradlew.bat`** — works, but `*.bat` will also
   ignore any future build scripts a contributor adds. Narrow it to `/run/*.bat`.
6. **Uncommitted working-tree changes** to `dependencies.gradle` and
   `gradle/gradle-daemon-jvm.properties` — commit or revert before review.
7. **CI** only runs on `master`/`main` push/PR. Add `workflow_dispatch` for manual runs.
8. **`mcmod.info`** — `authorList` order and `updateUrl` should point at the fork; `credits` is
   good.
9. ~~**No `src/test`** (§7.2) and no test task wired into CI.~~ **Done** — see §10.
10. **`CODEOWNERS`** correctly covers `/.github/**` — good.

---

## 9. Suggested fix order

| # | Change | Effort | Unblocks |
|---|---|---|---|
| 1 | Register FML-bus handlers on `FMLCommonHandler.instance().bus()` | 15 min | Config sync, time buttons, key overlay, book keyboard input (§1.1) |
| 2 | `registerConfig(Config.INSTANCE)` + key `ConfigProxy` lookups by class | 30 min | Config GUI (§1.2) |
| 3 | Rename `assets/theframework` → `assets/collectiveframework`; fix lang prefix; drop 4 stale keys | 20 min | Config GUI labels (§1.3, §4.3) |
| 4 | Revoke + remove the Yandex key; use `Config.yandexApiKey` | 20 min | Credential leak (§1.18) |
| 5 | Fix `getValue` returning `Field`; guard `writeToStream`'s `}` | 30 min | Config API correctness (§1.4, §1.5) |
| 6 | Delete `SimpleRunnable`/`SimpleThread`/`HeavyCalculations`; use `java.util.concurrent` | 2–3 h | CPU spin, Java 17 crash, broken `Future`s (§1.11–1.15) |
| 7 | Validate `TimeUpdatePacket`/`TileEntityServerUpdatePacket` against `ctx`'s player | 1–2 h | Two exploits (§1.16, §1.17) |
| 8 | Fix `TranslationManager` bounds check + cache key | 30 min | Translation (§1.8, §1.9) |
| 9 | Fix `Book.setPage`, `GuiBook` bus-leak/hit-test/arg-order, `GuiUtils.closeCurrentGui` | 1–2 h | Book API (§1.20, §2.18–2.20) |
| 10 | Cache `ImageIO` dimensions in `GuiBook` | 30 min | ~360 PNG decodes/sec (§5.1) |
| 11 | Fix the six `equals`-without-`hashCode` classes and `Location.hashCode` | 30 min | Math/geometry correctness (§2.14, §3.15) |
| 12 | Replace the config INI parser with Forge `Configuration` | 1 day | §1.5, §1.6, §2.8, §2.9, §3.18 |
| 13 | ~~Add `src/test` with the round-trip tests from §7.2~~ | — | **Done**, see §10 |
| 14 | Delete the dead code in §4 | 1 h | −300 lines, −4 resources |
| 15 | Rewrite `README.md`; fix `gradle.properties` publishing/group | 1 h | Repo hygiene (§8) |

---

## Appendix A — Verified-by-bytecode notes

These are the non-obvious facts I checked against the actual 1.7.10 jars rather than assuming:

| Claim | Verification |
|---|---|
| `MinecraftForge.EVENT_BUS` ≠ `FMLCommonHandler.instance().bus()` | `MinecraftForge.static{}` does `putstatic EVENT_BUS = new EventBus()`; `FMLCommonHandler.bus()` returns a separate field |
| `PlayerEvent`, `TickEvent`, `InputEvent` go to the FML bus | `firePlayerLoggedIn`, `firePlayerLoggedOut`, `onPreClientTick`, `onPostClientTick`, `fireKeyInput`, `fireMouseInput` all `invokevirtual bus()` then `EventBus.post` |
| `@SubscribeEvent` on a `@Mod` class is **not** auto-registered | `FMLModContainer` has no `AutomaticEventSubscriber`; `registerBus` registers `this` (the container) only |
| `Property.Type.tryParse(char)` falls back to `STRING` | confirmed in `Property.java` |
| `NBTTagCompound.getTagList(key, type)` returns the stored list when `tagCount()==0` | confirmed in `NBTTagCompound.java:345-357` — so `NBTHelper.getList` is *not* broken |
| `NBTTagList.getCompoundTagAt(i)` is bounds-safe | confirmed `NBTTagList.java:139-149` — so `TileEntityInventory.readFromNBT` is *not* an AIOOBE |
| `Locale.loadLocaleDataFiles` iterates **all** resource domains | `Locale.java:32-56` — so `assets/theframework/lang/` *does* load even though the mod id differs |
| `GuiConfig.drawScreen` null-guards `titleLine2` | `GuiConfig.java:346-354` — so the `null` from `getFilePath` is harmless |
| `FMLNetworkHandler.openGui` no-ops server-side when `getServerGuiElement` returns null | `EntityPlayer.openGui` → `FMLNetworkHandler.openGui`; `getServerGuiElement` returns null in `GuiHandler.java:15` — so the book is client-only, which is consistent |
| `MapStorage.setData` throws on a duplicate id | drives the `LocalBookData.createData` ordering hazard (§2.2) |

---

## 10. Test infrastructure (implemented)

This section describes the harness that was added so that the refactors above can be done safely. It
mirrors the `MrFuzzihead/OpenPeripheral` fork: JUnit 4 + Mockito for pure logic, JaCoCo for coverage,
and a separate `gameTest` source set that boots a real dedicated server and asserts from inside the
game.

### 10.1 Layout

| Path | Purpose |
|---|---|
| `addon.gradle.kts` | JaCoCo wiring, coverage floors, `gameTest` source set, `gameTestServer` task, `-PsmokeTest` hook |
| `dependencies.gradle` | JUnit 4.13.2, Mockito 4.11.0 (`core` + `inline`) |
| `src/test/java/**` | 349 unit tests (315 active, 34 `@Ignore`d known defects) |
| `src/gameTest/java/**` | `GameTestMod` — 61 in-game assertions, booted by `gameTestServer` |
| `src/main/java/.../SmokeTestHook.java` | CI-only: exits the JVM with a marker once FML finished loading |
| `.github/workflows/smoke-test.yml` | server smoke test + in-game game test on every PR |

Both extra source sets are excluded from the published jar.

### 10.2 Commands

```bash
./gradlew test                        # unit tests + JaCoCo exec data
./gradlew jacocoTestReport            # HTML/XML report -> build/reports/jacoco/test/html/index.html
./gradlew jacocoTestCoverageVerification   # enforce the floor
./gradlew check                       # everything (incl. the floor, spotless, checkstyle)
./gradlew gameTestServer              # boot a real dedicated server and run the in-game assertions
./gradlew runServer25 -PsmokeTest     # boot a server, log COLLECTIVEFRAMEWORK_SMOKE_TEST_OK, exit
```

### 10.3 Coverage baseline

Measured after batch 5 (§11):

| Counter | Covered / Total | Ratio | Floor |
|---|---|---|---|
| Instruction | 6,966 / 16,880 | 41.3% | — |
| Branch | 721 / 1,692 | 42.6% | 41% |
| **Line** | 1,212 / 3,338 | **36.3%** | **35%** |
| Complexity | 649 / 1,627 | 39.9% | — |
| Method | 348 / 779 | 44.7% | — |
| Class | 53 / 129 | 41.1% | — |

Per package: `multithreading` **85.8% line / 77.3% branch**, `language` **96.9% line**, `minecraft/threading`
covered by `MainThreadTest`, `minecraft/network` **40.5% line** (the rest needs a live connection).

The floors are a regression guard, not a target: most of the mod is Minecraft-coupled (GUIs,
packets, tile entities) and is only reachable from `gameTest`, which JaCoCo does not instrument.
Raise the floors as the pure-logic surface grows.

### 10.4 The known-defect workflow

Every defect above that is cheap to express as an assertion has **two** tests:

* a **passing** test that pins today's (wrong) behaviour, so a refactor that changes it is noticed;
* an **`@Ignore`d** test that states the *correct* behaviour, with the CODE_REVIEW section in the
  `@Ignore` reason, e.g.

```java
@Ignore("CODE_REVIEW §1.10: substring uses the wrong end index and throws")
@Test
public void replaceFirstPreservingCaseShouldWorkPastIndexZero() { ... }
```

So the refactor loop is: fix the defect → delete the `@Ignore` → the test must go green.

In `GameTestMod` the same idea uses `knownDefect(...)` instead of `check(...)`: it logs
`[KNOWN-DEFECT] <description>` and increments a counter but does not fail the run, so CI stays green
while the defect exists. When fixed, the assertion flips to `check(...)` automatically reporting
`(defect appears fixed - promote to check())`.

### 10.5 What the harness already proved

Running it against the unmodified code confirmed three findings from the static analysis, and found
one more:

1. **§1.2 is real, in-game.** `gameTestServer` reports:
   ```
   [KNOWN-DEFECT] the registered config proxy holds Config.INSTANCE (CODE_REVIEW §1.2)
   [KNOWN-DEFECT] ConfigRegistry.getFilePath(Config.INSTANCE) resolves (CODE_REVIEW §1.2)
   ```
   i.e. the config GUI is genuinely empty on a live server.
2. **§3.18 is not theoretical.** With JaCoCo instrumentation active, the synthetic `$jacocoData`
   field was discovered by `ConfigProxy` and written into the config file:
   ```
   General {
       # None! Tell the mod author to include a comment!
       Boolean Array:$jacocoData=[true,true]
   }
   ```
   Any coverage-instrumented build corrupts every user's config. The harness excludes test classes
   from instrumentation so fixtures stay clean, and `ConfigProxyTest` keeps an `@Ignore`d assertion
   for the underlying defect.
3. **§3.14 was mis-described.** `findLowestCommonDenominator` returns the *smallest* common divisor,
   not the GCD, so `Ratio(1920, 1080)` reduces to 960:540 rather than 16:9. `isWholeNumber(3.5)` is
   also `true`. Both are now pinned by `MathUtilsTest`/`RatioTest` and the entry above was corrected.
4. **New:** `TileEntityInventory` ships `removeStackFromSlotStatic` but never wires it into
   `IInventory#getStackInSlotOnClosing`, so every subclass must implement that method itself
   (recorded in §6.7).

### 10.6 Suggested additions as the refactor progresses

* A `TranslationManagerTest` using a fake `ITranslationProvider` — currently blocked because
  `translators` is a private static list with no reset hook, and `YandexProvider` is registered in a
  static initialiser. Adding a package-private `clearProviders()` would make §1.8/§1.9 testable.
* A `SimpleRunnable`/`HeavyCalculations` test once they are rewritten on `java.util.concurrent` —
  the current implementation cannot be tested without a running game (and throws on Java 17+).
* A client-side game test (Xvfb) that opens the config GUI and asserts a non-empty category list,
  which is the only way to cover §1.2/§1.3 end to end.

---

## 11. Fix log

### Batch 1 — config subsystem and event wiring

Scope: the six "quick win" items from §9 plus the defects that became reachable once the event buses
were wired up correctly.

| Review ref | Defect | Fix |
|---|---|---|
| **§1.1** | `PlayerEvent`, `TickEvent` and `InputEvent` handlers were registered on `MinecraftForge.EVENT_BUS` but those events are posted to `FMLCommonHandler.instance().bus()`, so config sync, the time-change buttons, the key overlay and book keyboard input were all dead | `CommonProxy`/`ClientProxy` now register FML-event handlers on the FML bus and Forge-event handlers on the Forge bus; `GuiBook` registers on both, because `MouseEvent` is Forge and `InputEvent` is FML |
| **§1.2** | `new Config()` was registered while the GUI passed `Config.INSTANCE`; `ConfigRegistry` matched by identity, so the config screen was always empty | `CollectiveFramework` registers `Config.INSTANCE`, and `ConfigProxy#isFor` now matches **by class** so any instance of a registered config class resolves |
| **§1.3** | Config GUI lang keys were built from `Reference.MOD_ID` (`collectiveframework`) but the lang file declared `theframework.lang.config.*`; the asset domain did not match the mod id either | Renamed `assets/theframework` → `assets/collectiveframework`, rewrote every key to the `collectiveframework.*` prefix (including the keybinding keys), and dropped the four stale `Misc.*` keys |
| **§1.4** | `DefaultConfigurationHandler#getValue` returned the `java.lang.reflect.Field` instead of the value | Returns `field.get(config)`; logs and returns `null` on `IllegalAccessException` |
| **§1.5** | `writeToStream` emitted a category's closing brace only when the loop reached `i == size`, so a trailing `clientSideOnly` field on a dedicated server produced an unterminated block and an unparseable file | The block is opened on the first written field and always closed; verified by a write/read/write round-trip test |
| **§1.18** | A live Yandex API key was hard-coded in `YandexProvider`, and the `yandexApiKey` config option that was supposed to replace it was never read | Key removed from source and now supplied via `YandexProvider#setApiKey`; `MinecraftTranslator` pushes `Config.yandexApiKey` in on every translate call, so GUI edits and server syncs take effect immediately; all entry points fail fast with `NOT_ENOUGH_PARAMETERS` when no key is set |
| **§2.18** | `GuiBook` registered itself on the event bus *before* validating the held item, so a failed construction left a half-built listener subscribed (`book == null` → NPE on the next event) | Registration moved to the end of the constructor; `onGuiClosed` null-guards `book` and unregisters from both buses |
| — | `ConfigRegistry.onConfigReload` used the config *string* as a file name in the revert branch | Uses `proxy.fileName` |
| — | `ConfigRegistry.registerConfig(null)` threw NPE | Throws `ConfigException` |
| — | `MinecraftForge.EVENT_BUS.register(new ConfigRegistry())` — `ConfigRegistry` has no `@SubscribeEvent` methods, so this registration did nothing | Removed |
| — | `YandexProvider` did not URL-encode the request, ignored its `text` argument in `detectLangauge`, and did not handle non-200 responses | `URLEncoder.encode(..., "UTF-8")`, `detectLangauge` uses its argument, explicit `switch` over the response code with a `default` case |
| — | `ITranslationProvider#detectLangauge` could not report a bad response | Now declares `TranslationException` |

**Behaviour change to be aware of:** `onClientDisconnect` no longer sends a "revert" `ConfigPacket` to
a player who is logging out — that packet can never be delivered (`PlayerLoggedOutEvent` is
server-side, and the config needing reverting lives on the client). It now subscribes to
`FMLNetworkEvent.ClientDisconnectionFromServerEvent` and calls the new
`ConfigRegistry#revertSyncedConfigs()`, which reloads each `doesSync` config from its local file.
The `ConfigPacket.isRevert` field is retained for API compatibility but is no longer sent by the
framework.

**Tests updated**

| Test | Change |
|---|---|
| `ConfigFileRoundTripTest#trailingClientOnlyFieldShouldNotBreakTheFile` | `@Ignore` removed — now the §1.5 regression guard |
| `ConfigFileRoundTripTest#trailingClientOnlyFieldSurvivesAReload` | New — proves the file survives a write/read/write cycle |
| `ConfigRegistryTest` | New (14 tests) — identity matching by class, `getValue` returning the value not the `Field`, `revertSyncedConfigs` behaviour, null/unannotated registration rejection |
| `YandexProviderTest` | New (8 tests) — key plumbing, fail-fast guards, and an assertion that no `trnsl.*` literal is compiled into the class |
| `GameTestMod` | §1.2 promoted from `knownDefect` to `check`; new `checkEventBusWiring` asserts (via `EventBus.listeners`) that `CollectiveFramework.instance` is registered on the FML bus and that the two buses are distinct instances. 28 → 33 checks, 0 known defects remaining |

Unit tests: 194 → **217** (183 active, 34 `@Ignore`d). Coverage: 24.3% → **25.5%** line, 35.6% →
**36.6%** branch; floors raised to 24% / 35%.

### Batch 2 — network packet safety

Scope: the two P0 exploits in the network layer, the client-side crash in the same class, and the
truncated-payload half of §3.2.

| Review ref | Defect | Fix |
|---|---|---|
| **§1.16** | `TimeUpdatePacket` checked op status against a `GameProfile` **carried inside the packet**, so any client could claim to be an operator; the requested absolute time was then applied to **every** loaded world with no bound | The handler reads the player from `ctx.getServerHandler().playerEntity` and authorises against that connection, applies only to the sender's own `WorldServer`, and validates the request through the new `resolveRequestedTime(...)`: overflow-safe (`Math.addExact`), non-negative, and within `MAX_TIME_CHANGE_TICKS` (one in-game day x 72) of the world's current time. `message.profile` is documented as informational only |
| **§1.17** | `TileEntityServerUpdatePacket` took the world from the packet's dimension id and called `TileEntity#readFromNBT` on whatever was at those coordinates — any client could overwrite the full NBT (including `x`/`y`/`z`) of any tile entity in any dimension | New `INBTSyncable` opt-in interface with a single shared `applyTo(...)` gate; the world comes from `player.worldObj`; an 8-block interaction range check; null guards. `NetworkedTileEntity` now implements `INBTSyncable` so the interface is usable out of the box |
| **§1.7** | `TileEntityClientUpdatePacket#fromBytes` called `WorldUtils.getWorldFromDimensionId(...)`, which dereferences `MinecraftServer.getServer()` — always null on a physical client, so decoding the packet threw on every client | The dimension travels on the wire as `dimensionId`; the handler resolves the world from `Minecraft.getMinecraft().theWorld` and drops the update unless the dimension matches |
| **§3.2** | `fromBytes` assumed `ByteBufUtils.readTag` returns non-null; a truncated payload threw `IndexOutOfBoundsException` on the netty thread | All three packets check `buf.readableBytes() < 2` before reading, and null-check the tag |
| — | `TileEntity#readFromNBT` overwrites the coordinates with whatever the tag says (0 when absent), which is the mechanism by which a forged payload could relocate a tile entity | `INBTSyncable#readFromNbtKeepingPosition(TileEntity, NBTTagCompound)` restores the position the world owns; used by `NetworkedTileEntity` |
| — | `TileEntityServerUpdatePacket`/`TileEntityClientUpdatePacket` NPE'd when no tile entity existed at the coordinates | Null-safe through `applyTo` |

**API changes to be aware of**

* `INBTSyncable` is new and **required** for both tile-entity sync packets. Tile entities that
  previously relied on blind `readFromNBT` must implement it. `NetworkedTileEntity` does so already.
* `ITranslationProvider#detectLangauge` now declares `TranslationException` (batch 1).
* `YandexProvider` no longer has an `API_KEY` constant; use `setApiKey` (batch 1).
* Both tile-entity packets gained a public `dimensionId` field, and `world` is now `null` on the
  receiving side. `TimeUpdatePacket` gained `MAX_TIME_CHANGE_TICKS` and `resolveRequestedTime`.

**Deliberately not done in this batch:** the handlers still run on the netty thread (§3.1). 1.7.10 has
no `MinecraftServer` task queue (`Minecraft#func_152344_a` exists, the server equivalent does not), so
scheduling onto the main thread needs a small shared tick-driven queue. That belongs with the
`SimpleRunnable`/`HeavyCalculations` replacement, which will provide exactly that primitive.

**Tests added**

| Test | Coverage |
|---|---|
| `INBTSyncableTest` | 6 tests — the type gate (the exploit), null world/payload, empty position, correct position only. Uses Mockito for `World` |
| `TimeUpdatePacketTest` | 11 tests — accept/reject around the limit, negative result, `Math.addExact` overflow, spoofed absolute time, constants |
| `TileEntityServerUpdatePacketTest` | 9 tests — the 8-block range check via a Mockito `EntityPlayer`, netty round trip, missing payload, truncated buffer, null tag |
| `TileEntityClientUpdatePacketTest` | 7 tests — netty round trip, `dimensionId` on the wire, `world` null after decode (no `MinecraftServer`), `isDimensionMatch`, truncated buffer |
| `GameTestMod` | +15 checks: a real vanilla chest is rejected by the sync gate, a real opted-in tile entity accepts a payload, the payload does not relocate it, and the time bounds hold. 33 -> 48 checks |

Unit tests: 217 -> **249** (215 active, 34 `@Ignore`d). Coverage: 25.5% -> **27.2%** line, 36.6% ->
**37.0%** branch; floors raised to 26% / 36%.

### Batch 3 — translation provider selection and cache

Scope: `TranslationManager`, which was the smallest remaining cluster and whose private static provider
list blocked testing.

| Review ref | Defect | Fix |
|---|---|---|
| **§1.8** | Provider selection did `if (!translators.contains(i))` — a `List<ITranslationProvider>.contains(Integer)`, which is always `false` — so the first unusable provider threw `NO_VALID_PROVIDERS` instead of falling through. It also evaluated `translators.get(i)` **before** the bounds check, so the intended exception was replaced by `IndexOutOfBoundsException`. `detectLanguage` and the auto-detect overload had the same loop | Provider selection is now a plain loop over `usableProviders(requireDetect)` with no indexing and no recursion. A `QueryLimitException` disables that provider and tries the next; a request-level `TranslationException` is rethrown immediately rather than being retried against every provider |
| **§1.9** | The cache was keyed on the text alone, so translating `"Stone"` to `de` and then to `fr` returned the German string; it was an unbounded `HashMap` mutated from the render thread and the netty thread | New `CacheKey(text, fromLang, toLang)` (with `null` meaning auto-detect), stored in a synchronised access-ordered `LinkedHashMap` bounded at `MAX_CACHE_ENTRIES = 1024`. `translators` is a `CopyOnWriteArrayList` and `unusableProviders` a concurrent set |
| **§3.12** | Exhausting a provider recursed (`return translate(...)`), so a second exhaustion was a `StackOverflowError` | No recursion; the loop falls through and reports `NO_VALID_PROVIDERS` |
| — | The provider list and rate-limit set were unmodifiable private statics, so the class could not be tested at all | Added `clearProviders()`, `resetRateLimits()`, `clearCache()` and `getCacheSize()` |
| — | `translate`/`detectLanguage` accepted nulls and failed later with an NPE | Explicit `IllegalArgumentException` |

**Behaviour change to be aware of:** a `TranslationException` other than a query limit is no longer
retried against the remaining providers. That is deliberate — `TEXT_TOO_LONG`, `LANGUAGE_NOT_SUPPORTED`
and `NOT_ENOUGH_PARAMETERS` are properties of the request, and retrying them multiplied outbound API
calls by the number of registered providers.

**Tests added** — `TranslationManagerTest`, 21 tests using a `FakeProvider`:

| Area | Coverage |
|---|---|
| Provider selection | first provider wins; rate-limited provider falls through; every provider exhausted reports `NO_VALID_PROVIDERS`; no providers registered; auto-detect skips providers that cannot detect; auto-detect with no detector; auto-detect falls back after a rate limit; request-level failures are not retried; null arguments rejected |
| Cache | keyed per target language (§1.9 regression guard), per source language, auto-detect vs explicit source are distinct; repeated requests hit the cache; the cache is bounded; failures are not cached; `clearCache` |
| Rate limits | `resetRateLimits()` re-enables a disabled provider |
| `detectLanguage` | first detect-capable provider; falls back after a rate limit; throws when none can detect |
| Concurrency | 8 threads x 50 translations, asserting 30 distinct cache entries and no corruption (§1.9) |

`GameTestMod` also gained 2 checks proving that no API key is compiled in and that translation fails
fast with `NOT_ENOUGH_PARAMETERS` rather than making an unauthenticated request (48 → 50 checks).

Unit tests: 249 → **270** (236 active, 34 `@Ignore`d). Coverage: 27.2% → **29.1%** line, 37.0% →
**38.3%** branch; floors raised to 28% / 37%.

### Batch 4 — the threading layer

Scope: `SimpleRunnable`, `SimpleThread`, `HeavyCalculations`. The highest-risk batch, because it replaces a
hand-rolled thread pool that two internal callers and any number of external mods depend on.

| Review ref | Defect | Fix |
|---|---|---|
| **§1.11** | `SimpleRunnable#delay(int)` called `Object.wait()` on the backing thread without owning its monitor, so it always threw `IllegalMonitorStateException` | `Thread.sleep(...)`, which is interruptible and preserves the declared `InterruptedException` |
| **§1.12** | `HeavyCalculations.FutureImpl` busy-waited in `get()` (100% CPU), threw `IllegalMonitorStateException` from `get(timeout, unit)` (`wait(1)` with no monitor), never threw `TimeoutException`, and hung forever after `kill()` | Backed by a real `ExecutorService`; `submit` returns a real `Future`, so `get`, `get(timeout)`, `isDone`, `cancel` and `TimeoutException` all behave. `addCalculation` after `kill()` now throws `RejectedExecutionException` instead of queueing into the void |
| **§1.13** | The `SimpleThread` loop was `do { … } while (RESTRICT_THREAD_USAGE)`. With the flag `true` (the default) a disabled runnable spun forever doing nothing (one pegged core per runnable); with it `false` the thread exited after a single iteration, silently breaking every ticking task and `HeavyCalculations` | `SimpleRunnable` runs on a shared daemon `ScheduledThreadPoolExecutor`. `start()` repeats with a 1 ms floor between iterations, `startOnce()` runs once, `disable()` cancels the scheduled future |
| **§1.14** | `SimpleThread` reflectively wrote `Thread.class.getDeclaredField("target")` and read `"threadStatus"` — `InaccessibleObjectException` on Java 17+ | No reflection at all. `SimpleThread#setTarget` stores the target in a field of its own; `start()` uses `Thread#getState()` |
| **§1.15** | "Thread reuse" swapped the target of an already-terminated `Thread`, and `start()` never called `super.start()` when the status was non-zero, so a reused runnable never ran; `SimpleRunnable#thread` was never reset, so two runnables could share a pool index and compare `equals` | No pooling of `Thread` objects: the executor owns the threads |
| **§3.15** | `SimpleRunnable#equals` compared a pool index plus a name (so unrelated runnables could compare equal) with no `hashCode`; `SimpleThread#equals` had no `hashCode` | `SimpleRunnable` is identity-based with a stable id; `SimpleThread` gained the matching `hashCode` |

**Caller-compatibility audit** (every reference to these types in the repository):

| Caller | Before | After |
|---|---|---|
| `Book#setData` (GLOBAL storage) | `new SimpleRunnable(){ … this.disable(true); }.start()` | `startOnce()`, `disable(true)` removed — it only existed to stop the repeat loop |
| `DependencyManager.DownloadFuture.DownloadRunnable` | `runnable.start()` in `DownloadFuture`'s constructor | `startOnce()` in `DownloadRunnable`'s constructor |
| `DependencyManager.DownloadFuture#cancel` | `runnable.disable(true)` — a second `cancel()` threw `IllegalThreadStateException` | Idempotent, and `isCancelled` is `volatile` |
| `DependencyManager.DownloadRunnable#getName` | used the static counter, so every downloader shared the last name | uses the instance's `downloadNum` |
| `CollectiveFramework#preInit` | `SimpleRunnable.RESTRICT_THREAD_USAGE = Config.restrictThreadUsage` | Unchanged; the field is deprecated and has no effect, but still reports the configured value |

**API compatibility**

* No public method was removed from `SimpleRunnable`, `SimpleThread`, `HeavyCalculations` or
  `ICalculations`.
* `SimpleThread#setTarget` keeps its `throws NoSuchFieldException, IllegalAccessException` clause even
  though it can no longer throw them, so existing `try`/`catch` blocks still compile.
* `SimpleThread` is kept and kept working, marked `@Deprecated`, rather than deleted — deleting it would
  turn a dependency into a compile error. It is a candidate for removal in a major version.
* `SimpleRunnable.RESTRICT_THREAD_USAGE` is kept as a `@Deprecated` no-op for the same reason.
* New: `SimpleRunnable#startOnce()`, `#getTicking()`, `SimpleRunnable.getActiveTaskCount()`,
  `HeavyCalculations#awaitTermination(...)`.
* Behaviour changes, all deliberate and documented in the Javadoc:
  * `HeavyCalculations#addCalculation` after `kill()` throws `RejectedExecutionException` instead of
    hanging forever.
  * `HeavyCalculations(0)` throws `IllegalArgumentException` instead of NPE-ing on first use.
  * `HeavyCalculations#addCalculation(Class, String, Object...)` returns the wrapper from `get()`
    instead of `null`.
  * A `run()` that throws is logged and the loop continues, instead of killing the thread.
  * `SimpleRunnable#start()` after `disable(true)` throws `IllegalThreadStateException` (the old code
    NPE'd).
  * `HeavyCalculations` failures surface as `ExecutionException` instead of being swallowed by
    `printStackTrace`.

**Tests added** — 50 new unit tests, all of which had to handle real concurrency:

| Test | Coverage |
|---|---|
| `SimpleRunnableTest` (24) | `startOnce` runs exactly once and is re-runnable; `start` repeats until disabled; a disabled runnable releases its worker (asserted via the executor's active count, not just an invocation count, because the old bug spun *without* calling `run()`); `start` is idempotent; `disable` twice throws; start/`startOnce`/`enable` after a clean disable throw; `disable(false)` keeps the runnable usable; `enable` restarts; ticking is recorded, slows the loop, and can be changed while running; `delay` sleeps instead of throwing (§1.11) and is interruptible; a throwing `run` neither stops the loop nor escapes the executor; 32 concurrent runnables all complete; naming; identity equality and `hashCode` (§3.15); lifecycle flags |
| `SimpleThreadTest` (11) | target runs; `setTarget` works without reflection (§1.14); `start()` twice is safe; clearing `isActive` stops the loop; `RESTRICT_THREAD_USAGE` no longer controls the loop (§1.13); a throwing target does not kill the thread; delay honoured; state transitions; name-based equality with `hashCode` |
| `HeavyCalculationsTest` (15) | rejects 0/negative thread counts; runs the calculation and `get()` returns the passed object; 24 calculations across 4 threads; `get(timeout)` returns and **throws `TimeoutException`** (§1.12); `isDone`; instance and static reflective overloads with parameters; missing and throwing methods surface as `ExecutionException`; `ClassCastException` for non-`ICalculations`; `kill` marks dead, rejects new work, lets submitted work finish, `awaitTermination`, idempotent |
| `GameTestMod` (+8) | **The only place the Java 17+ regression can actually be proven**: `gameTestServer` runs on Java 25, and the first check asserts that `java.lang.Thread` really is closed to reflection on that JVM. It then runs `startOnce`, a ticking `start()`, `SimpleThread#setTarget` and a `HeavyCalculations` `TimeoutException` — all of which would have thrown `InaccessibleObjectException` before this batch |

The threading tests were run 5 times consecutively to check for timing flakiness; all green.

Unit tests: 270 → **320** (286 active, 34 `@Ignore`d). Coverage: 29.1% → **33.9%** line, 38.3% →
**41.4%** branch; floors raised to 32% / 40%.

### Batch 5 — main-thread scheduling and the dependency manager

Two clusters that share a theme: work that must not run on the thread it currently runs on.

#### §3.1 — packet handlers ran on the netty thread

Network message handlers in 1.7.10 are invoked on the netty event loop. All four packets touched world
state from there, and resolving a tile entity can load a chunk, which is not thread-safe.

1.7.10 has no `MinecraftServer` task queue — only `Minecraft#func_152344_a` exists, on the client — so
the framework now keeps its own. New `com.austinv11.collectiveframework.minecraft.threading.MainThread`:

* `schedule(Runnable)` / `scheduleOnServer` / `scheduleOnClient` queue into two `ConcurrentLinkedQueue`s;
* `MainThreadTickHandler` drains them on `TickEvent.Phase.END` (registered on the FML bus, per §1.1);
* a drain is capped at `MAX_TASKS_PER_DRAIN` (1000) so a self-rescheduling task cannot stall the tick, and
  one throwing task does not stop the rest.

Applied to every packet:

| Packet | Before | After |
|---|---|---|
| `ConfigPacket` | reloaded static config fields and wrote files on the netty thread | whole handler on the client thread |
| `TileEntityServerUpdatePacket` | range check and `applyTo` (chunk load) on the netty thread | whole handler on the server thread |
| `TileEntityClientUpdatePacket` | same, client side | whole handler on the client thread |
| `TimeUpdatePacket` | read and wrote world time on the netty thread | authorisation stays on the netty thread (cheap, no world access); the world read/write moves to the server thread |

#### §3.5 – §3.8 — the dependency manager

| Review ref | Defect | Fix |
|---|---|---|
| **§3.5** | `loadLibrary` built a `URLClassLoader` and discarded it — it loaded nothing and leaked the loader | Deprecated; logs a warning pointing at `MinecraftDependencyManager#loadLibrary` and no longer creates a loader. Kept as a no-op rather than made to throw, so existing callers do not start failing |
| **§3.6** | Provider selection set `provider` inside a doubly nested loop and broke only the inner one, so the **last** matching provider won, contradicting the documented "earlier registration = higher priority" | Extracted `findProvider(FileType)`, which returns the first match |
| **§3.7** | `DownloadFuture.get()` returned `runnable.didDownload` immediately and ignored the timeout, so a caller always saw `false` unless it happened to poll; `cancel()` threw on the second call | Real `Future` semantics: a `CountDownLatch` released in a `finally` block, so `get()` waits, `get(timeout)` throws `TimeoutException`, `isDone()` is accurate, and `cancel()` is idempotent and releases waiters with `CancellationException` |
| **§3.8** | `FileType.getFileTypeForOther` wrote the custom name into the shared `OTHER` enum constant, so two callers or two threads corrupted each other; `OTHER.toString()` returned `null` before that was called | Deprecated and no longer mutates; `toString()` always returns `name()`. Also made `numOfDownloaders` an `AtomicInteger` and `providers` a `CopyOnWriteArrayList` |

**API compatibility**

* Nothing public removed. `FileType.getFileTypeForOther` and `DependencyManager.loadLibrary` are
  `@Deprecated` but still present with unchanged signatures (including `throws MalformedURLException`).
* `DependencyManager.downloadFileAsync` still returns `Future<Boolean>`; the concrete `DownloadFuture`
  now narrows its `throws` clause, which is legal and invisible through the interface.
* New: `MainThread`, `MainThreadTickHandler`, `DependencyManager.clearDownloadProviders()`.
* Behaviour change to be aware of: `FileType.OTHER.toString()` is `"OTHER"` rather than a custom name.
  A caller that relied on `getFileTypeForOther("x").toString().equals("x")` was relying on global mutable
  state that broke under concurrency.

**A harness bug this batch uncovered.** The game test registers a block, which FML records in
`level.dat`. A later run *without* the game-test source set — the smoke test — then saw it as a missing
block and blocked on an interactive `/fml confirm` prompt for exactly ten minutes. CI never hit it
because `run/` is not checked in, but locally it made the smoke test look like a 10-minute hang. Both
Gradle entry points now pass `-Dfml.queryResult=confirm`, so no run can ever block on that prompt.

**Tests added** — 29 new unit tests plus 3 in-game checks:

| Test | Coverage |
|---|---|
| `MainThreadTest` (13) | scheduling does not run the task inline; drain runs it; FIFO order across 20 tasks; server and client queues are independent; `clear` discards; null rejected; a throwing task does not stop the queue; a task may schedule another task and both run in one pass; **the drain is bounded** (a self-rescheduling task runs exactly `MAX_TASKS_PER_DRAIN` times and the surplus is left queued); draining an empty queue; 800 tasks scheduled from 8 threads are all drained; `schedule` falls back to the server queue without FML |
| `DependencyManagerTest` (16) | the first matching provider wins (§3.6); selection by capability; `NoProviderFoundException` sync and async; null provider rejected; `get()` **waits** for the result (§3.7); `get(timeout)` waits and **throws `TimeoutException`**; a failed download reports `false`; a throwing provider still releases the waiter; `cancel` is idempotent and yields `CancellationException`; `loadLibrary` is a no-op that does not throw (§3.5); `FileType` stringification and non-mutation (§3.8) |
| `GameTestMod` (+3) | schedules a task from outside the tick and asserts, on a later tick, that `MainThread` drained it **on the server thread**, within a few ticks, leaving the queue empty. This exercises the real `TickEvent` wiring rather than calling the drain directly |

Unit tests: 320 → **349** (315 active, 34 `@Ignore`d). Coverage: 33.9% → **36.3%** line, 41.4% →
**42.6%** branch; floors raised to 35% / 41%.

### Remaining batches

Still open: the book API (§1.20, §2.1, §2.2, §2.19, §2.20), the config parser replacement
(§1.6/§2.8/§2.9/§3.18 — §1.5 is fixed but the parser is still fragile), the HTTP/`FileUtils`
robustness items (§3.4, §3.9), the geometry and `MathUtils` correctness items (§2.10–§2.16, §3.13,
§3.14), the remaining performance items in §5, and the dead-code cleanup in §4.

The book API is now the largest remaining cluster and contains one P0 crash (§1.20) plus two data-loss
defects (§2.1, §2.2).
