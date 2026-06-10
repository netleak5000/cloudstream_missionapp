# CloudStream Mission App - Modifications Guide

## Overview

This document outlines all custom modifications made to the original CloudStream to create the CloudStream Mission App. The goal is to keep original files untouched and maintain easy integration with future upstream updates.

---

## Custom Modifications Summary

### 1. **Authentication System (NEW FILES)**
- **Files**: `LoginActivity.kt`, `RegisterActivity.kt`, `AuthManager.kt`, `SubscriptionActivity.kt`
- **Purpose**: Custom JWT-based authentication system for user login/registration
- **Integration Point**: Mission App custom backend (netleak.nl)
- **Status**: ✅ Modular - No modifications to original files

---

### 2. **Token Management & Session Refresh (MainActivity.kt)**

#### Location: Lines 213-293 in MainActivity.kt (marked with `/*ankit open*/ ... /*ankit close*/`)

**Modifications:**
```kotlin
// Added OkHttpClient for token validation
private val httpClient = OkHttpClient()

// New function: Token validation check
private fun isTokenValid(token: String): Boolean { ... }

// New function: Automatic token refresh
private fun refreshToken() { ... }
```

**Why**: Prevents session expiration by automatically refreshing JWT tokens
**Integration**: Call `refreshToken()` in `onCreate()` or `onStart()` after the original code

---

### 3. **Plugin Loading Enhancement (MainActivity.kt)**

#### Location: Lines 856-909 (marked with `/*ankit_open*/ ... /*ankit_closed*/`)

**Modifications:**
```kotlin
private fun onAllPluginsLoaded(success: Boolean = false) {
    // Original code for standard plugins
    // PLUS: Added logic to merge custom provider clones
    
    // Build a list of cloned/custom providers
    val newProviders = mutableListOf<MainAPI>()
    getKey<Array<SettingsGeneral.CustomSite>>(USER_PROVIDER_API)?.forEach { custom ->
        // Clone existing providers with custom settings
        allProviders.firstOrNull { it.javaClass.simpleName == custom.parentJavaClass }?.let {
            newProviders.add(it.javaClass.getDeclaredConstructor().newInstance().apply {
                name = custom.name
                lang = custom.lang
                mainUrl = custom.url.trimEnd('/')
                canBeOverridden = false
            })
        }
    }
    
    // Merge and deduplicate
    val mergedProviders = (allProviders + newProviders).distinctBy {
        "${it.lang}-${it.name}-${it.mainUrl}-${it.javaClass.name}"
    }
    
    allProviders.clear()
    allProviders.addAll(mergedProviders)
}
```

**Why**: Allows custom provider clones to be loaded alongside standard plugins
**Change Type**: Enhancement to existing function

---

### 4. **OnResume Event Handler Enhancement (MainActivity.kt)**

#### Location: Lines 740-759 (marked with `/*ankit open*/ ... /*ankit close*/`)

**Modifications:**
```kotlin
override fun onResume() {
    super.onResume()
    
    /*ankit open replace existing*/
    // Use minusAssign and plusAssign to be explicit
    afterPluginsLoadedEvent.minusAssign(::onAllPluginsLoaded)
    afterPluginsLoadedEvent.plusAssign(::onAllPluginsLoaded)
    
    // Safe null checks for mSessionManager
    if (isCastApiAvailable()) {
        mSessionManager?.let { manager ->
            mSessionManagerListener?.let { listener ->
                manager.addSessionManagerListener(listener)
            }
        }
    }
    /*ankit close*/
}
```

**Why**: Prevents duplicate event listeners and adds null safety
**Change Type**: Safety improvement to existing code

---

### 5. **Preference Keys for Custom Features (NEW FILE)**

**File**: `PrefKeys.kt`

```kotlin
object PrefKeys {
    const val JWT_TOKEN = "jwt_token"
    const val IS_LOGGED_IN = "isLoggedIn"
    const val REMEMBER = "rememberMe"
    const val USERNAME = "username"
    const val PASSWORD = "password"
    const val SAVED_USERNAME = "saved_username"
    const val SAVED_PASSWORD = "saved_password"
    const val DEVICE_ID = "device_id"
}
```

**Why**: Centralized constants for authentication preferences
**Status**: ✅ Modular - New file, no original file modifications

---

## Comparison: Original vs Modified

| Feature | Original | Modified | File | Type |
|---------|----------|----------|------|------|
| Authentication | None | JWT-based | `AuthManager.kt` (NEW) | New Module |
| Login Screen | None | Custom | `LoginActivity.kt` (NEW) | New Module |
| Registration | None | Custom | `RegisterActivity.kt` (NEW) | New Module |
| Token Refresh | None | Auto-refresh | `MainActivity.kt` (Lines 213-293) | Enhancement |
| Plugin Loading | Basic | Enhanced with clones | `MainActivity.kt` (Lines 856-909) | Enhancement |
| Event Safety | Basic | Improved null checks | `MainActivity.kt` (Lines 740-759) | Safety Fix |

---

## How to Integrate Future Updates

### Step 1: Download New Original Version
```bash
git fetch upstream master  # or pull latest from original repo
```

### Step 2: Update Original Files Only
- Copy updated files from upstream that don't have modifications:
  - Any files NOT listed in the modifications above are safe to update directly
  - Files like `CloudStreamApp.kt`, `CommonActivity.kt`, etc. can be updated as-is

### Step 3: Manually Merge Modified Files
For files with modifications (`MainActivity.kt`, etc.):

1. **Backup current version**
   ```bash
   cp app/src/main/java/com/lagradost/cloudstream3/MainActivity.kt MainActivity.kt.backup
   ```

2. **Get new version from upstream**
   ```bash
   git checkout upstream/master -- app/src/main/java/com/lagradost/cloudstream3/MainActivity.kt
   ```

3. **Manually re-apply custom code sections**
   - Use merge markers or three-way diff
   - Only re-add sections marked with `/*ankit open/close*/` comments
   - Verify no conflicts with new code

4. **Test thoroughly**
   - Verify authentication still works
   - Test plugin loading
   - Check token refresh mechanism

### Step 4: Update Custom Authentication Module
- Keep `LoginActivity.kt`, `RegisterActivity.kt`, `AuthManager.kt`, `SubscriptionActivity.kt` unchanged
- They are independent and don't conflict with upstream

---

## File Organization

```
cloudstream_missionapp/
├── README.md
├── MODIFICATIONS_GUIDE.md (this file)
├── cloudstream3/
│   ├── AcraApplication.kt              (✅ SAFE - Standard wrapper)
│   ├── AuthManager.kt                  (🆕 NEW - Custom auth)
│   ├── CloudStreamApp.kt               (ORIGINAL - Update with upstream)
│   ├── CommonActivity.kt               (ORIGINAL - Update with upstream)
│   ├── LoginActivity.kt                (🆕 NEW - Custom login)
│   ├── MainActivity.kt                 (⚠️  MODIFIED - Merge carefully)
│   ├── PrefKeys.kt                     (🆕 NEW - Custom prefs)
│   ├── RegisterActivity.kt             (🆕 NEW - Custom register)
│   ├── SubscriptionActivity.kt         (🆕 NEW - Custom subscription)
│   ├── DownloaderTestImpl.kt            (ORIGINAL)
│   ├── PluginResetManager.kt           (ORIGINAL)
│   └── [other original files]
```

**Legend:**
- 🆕 NEW = Custom files, safe to keep as-is
- ⚠️  MODIFIED = Contains custom code, requires careful merging
- ✅ SAFE = Safe to update directly from upstream
- ORIGINAL = Can be updated from upstream

---

## Quick Reference: Modified Code Sections

### MainActivity.kt Modified Sections:

1. **Lines 213-228**: `isTokenValid()` function
2. **Lines 230-292**: `refreshToken()` function  
3. **Lines 740-758**: Enhanced `onResume()` method
4. **Lines 856-909**: Enhanced `onAllPluginsLoaded()` method

All marked with `/*ankit open*/` and `/*ankit close*/` comments for easy identification.

---

## Testing Checklist for Updates

After integrating upstream changes:

- [ ] App launches without crashes
- [ ] Authentication flow works (login/register)
- [ ] JWT token refresh works automatically
- [ ] Custom provider clones load correctly
- [ ] Original plugins load alongside custom ones
- [ ] No null pointer exceptions on cast operations
- [ ] Event listeners don't duplicate

---

## Troubleshooting

### Problem: Duplicate Event Listeners
**Solution**: Ensure `minusAssign` is called before `plusAssign` in `onResume()`

### Problem: Token Validation Fails
**Solution**: Check JWT endpoint is accessible and token format is correct

### Problem: Custom Providers Don't Load
**Solution**: Verify `USER_PROVIDER_API` key in settings contains valid provider data

### Problem: Merge Conflicts After Update
**Solution**: 
1. Keep all original code from upstream
2. Re-add custom sections carefully using line markers
3. Test each section independently

---

## Future Maintenance

For each new upstream version:

1. **Identify changed files** - use git diff
2. **For unchanged files**: Update directly
3. **For modified files**: 
   - Document new changes from upstream
   - Re-merge carefully keeping custom sections
   - Add integration comments
4. **Test and commit** - Create PR for verification
5. **Update this guide** if new modifications are needed

---

**Last Updated**: June 10, 2026  
**Mod Version**: Mission App v1.0  
**Original Base**: CloudStream (recloudstream/cloudstream)
