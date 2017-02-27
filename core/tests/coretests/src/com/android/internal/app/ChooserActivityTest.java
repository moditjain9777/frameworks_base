/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.app;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.SystemClock;
import com.android.internal.R;
import com.android.internal.app.ResolverActivity.ResolvedComponentInfo;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static com.android.internal.app.ChooserWrapperActivity.sOverrides;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Chooser activity instrumentation tests
 */
@RunWith(AndroidJUnit4.class)
public class ChooserActivityTest {
    private Instrumentation instrumentation;

    @Before
    public void setUp() {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        sOverrides.reset();
    }

    @Rule
    public ActivityTestRule<ChooserWrapperActivity> mActivityRule =
            new ActivityTestRule<>(ChooserWrapperActivity.class, false,
                    false);

    @Test
    public void customTitle() throws InterruptedException {
        Intent sendIntent = createSendImageIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        when(sOverrides.resolverListController.getResolversForIntent(Mockito.anyBoolean(),
                Mockito.anyBoolean(),
                Mockito.isA(List.class))).thenReturn(resolvedComponentInfos);
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, "chooser test"));
        waitForIdle();
        onView(withId(R.id.title)).check(matches(withText("chooser test")));
    }

    @Test
    public void emptyTitle() throws InterruptedException {
        Intent sendIntent = createSendImageIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        when(sOverrides.resolverListController.getResolversForIntent(Mockito.anyBoolean(),
                Mockito.anyBoolean(),
                Mockito.isA(List.class))).thenReturn(resolvedComponentInfos);
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        onView(withId(R.id.title))
                .check(matches(withText(R.string.whichSendApplication)));
    }

    @Test
    public void twoOptionsAndUserSelectsOne() throws InterruptedException {
        Intent sendIntent = createSendImageIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        when(sOverrides.resolverListController.getResolversForIntent(Mockito.anyBoolean(),
                Mockito.anyBoolean(),
                Mockito.isA(List.class))).thenReturn(resolvedComponentInfos);

        final ChooserWrapperActivity activity = mActivityRule
                .launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        assertThat(activity.getAdapter().getCount(), is(2));
        onView(withId(R.id.profile_button)).check(matches(not(isDisplayed())));

        ResolveInfo[] chosen = new ResolveInfo[1];
        sOverrides.onSafelyStartCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };

        ResolveInfo toChoose = resolvedComponentInfos.get(0).getResolveInfoAt(0);
        onView(withText(toChoose.activityInfo.name))
                .perform(click());
        waitForIdle();
        assertThat(chosen[0], is(toChoose));
    }

    @Test
    public void updateChooserCountsAndModelAfterUserSelection() throws InterruptedException {
        Intent sendIntent = createSendImageIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        when(sOverrides.resolverListController.getResolversForIntent(Mockito.anyBoolean(),
                Mockito.anyBoolean(),
                Mockito.isA(List.class))).thenReturn(resolvedComponentInfos);

        final ChooserWrapperActivity activity = mActivityRule
                .launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        UsageStatsManager usm = activity.getUsageStatsManager();
        verify(sOverrides.resolverListController, times(1))
                .sort(Mockito.any(List.class));
        assertThat(activity.getIsSelected(), is(false));
        sOverrides.onSafelyStartCallback = targetInfo -> {
            return true;
        };
        ResolveInfo toChoose = resolvedComponentInfos.get(0).getResolveInfoAt(0);
        onView(withText(toChoose.activityInfo.name))
                .perform(click());
        waitForIdle();
        verify(sOverrides.resolverListController, times(1))
                .updateChooserCounts(Mockito.anyString(), Mockito.anyInt(), Mockito.anyString());
        verify(sOverrides.resolverListController, times(1))
                .updateModel(toChoose.activityInfo.getComponentName());
        assertThat(activity.getIsSelected(), is(true));
    }

    @Test
    public void reportChooserSelection() throws InterruptedException {
        Intent sendIntent = createSendImageIntent();
        final ChooserWrapperActivity activity = mActivityRule
                .launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        UsageStatsManager usm = activity.getUsageStatsManager();
        String packageName = "test_package";
        String action = "test_action";
        String annotation = "test_annotation";
        long beforeReport = getCount(usm, packageName, action, annotation);
        usm.reportChooserSelection(packageName, activity.getUserId(), annotation, null, action);
        long afterReport = getCount(usm, packageName, action, annotation);
        assertThat(afterReport, is(beforeReport + 1l));
    }

    @Test
    public void noResultsFromPackageManager() {
        when(sOverrides.resolverListController.getResolversForIntent(Mockito.anyBoolean(),
                Mockito.anyBoolean(),
                Mockito.isA(List.class))).thenReturn(null);
        Intent sendIntent = createSendImageIntent();
        final ChooserWrapperActivity activity = mActivityRule
                .launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        assertThat(activity.isFinishing(), is(false));

        onView(withId(R.id.empty)).check(matches(isDisplayed()));
        onView(withId(R.id.resolver_list)).check(matches(not(isDisplayed())));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> activity.getAdapter().handlePackagesChanged()
        );
        // backward compatibility. looks like we finish when data is empty after package change
        assertThat(activity.isFinishing(), is(true));
    }

    @Test
    public void autoLaunchSingleResult() throws InterruptedException {
        ResolveInfo[] chosen = new ResolveInfo[1];
        sOverrides.onSafelyStartCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };

        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(1);
        when(sOverrides.resolverListController.getResolversForIntent(Mockito.anyBoolean(),
                Mockito.anyBoolean(),
                Mockito.isA(List.class))).thenReturn(resolvedComponentInfos);

        Intent sendIntent = createSendImageIntent();
        final ChooserWrapperActivity activity = mActivityRule
                .launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        assertThat(chosen[0], is(resolvedComponentInfos.get(0).getResolveInfoAt(0)));
        assertThat(activity.isFinishing(), is(true));
    }

    @Test
    public void hasOtherProfileOneOption() throws Exception {
        Intent sendIntent = createSendImageIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(2);
        ResolveInfo toChoose = resolvedComponentInfos.get(1).getResolveInfoAt(0);

        when(ChooserWrapperActivity.sOverrides.resolverListController.getResolversForIntent(
                Mockito.anyBoolean(),
                Mockito.anyBoolean(),
                Mockito.isA(List.class))).thenReturn(resolvedComponentInfos);

        final ChooserWrapperActivity activity = mActivityRule
                .launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        // The other entry is filtered to the other profile slot
        assertThat(activity.getAdapter().getCount(), is(1));

        ResolveInfo[] chosen = new ResolveInfo[1];
        ChooserWrapperActivity.sOverrides.onSafelyStartCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };
        // Make a stable copy of the components as the original list may be modified
        List<ResolvedComponentInfo> stableCopy =
                createResolvedComponentsForTestWithOtherProfile(2);
        // Check that the "Other Profile" activity is put in the right spot
        onView(withId(R.id.profile_button)).check(matches(
                withText(stableCopy.get(0).getResolveInfoAt(0).activityInfo.name)));
        onView(withText(stableCopy.get(1).getResolveInfoAt(0).activityInfo.name))
                .perform(click());
        waitForIdle();
        assertThat(chosen[0], is(toChoose));
    }

    @Test
    public void hasOtherProfileTwoOptionsAndUserSelectsOne() throws Exception {
        Intent sendIntent = createSendImageIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(3);
        ResolveInfo toChoose = resolvedComponentInfos.get(1).getResolveInfoAt(0);

        when(ChooserWrapperActivity.sOverrides.resolverListController.getResolversForIntent(
                Mockito.anyBoolean(),
                Mockito.anyBoolean(),
                Mockito.isA(List.class))).thenReturn(resolvedComponentInfos);
        when(ChooserWrapperActivity.sOverrides.resolverListController.getLastChosen())
                .thenReturn(resolvedComponentInfos.get(0).getResolveInfoAt(0));

        final ChooserWrapperActivity activity = mActivityRule
                .launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        // The other entry is filtered to the other profile slot
        assertThat(activity.getAdapter().getCount(), is(2));

        ResolveInfo[] chosen = new ResolveInfo[1];
        ChooserWrapperActivity.sOverrides.onSafelyStartCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };

        // Make a stable copy of the components as the original list may be modified
        List<ResolvedComponentInfo> stableCopy =
                createResolvedComponentsForTestWithOtherProfile(3);
        // Check that the "Other Profile" activity is put in the right spot
        onView(withId(R.id.profile_button)).check(matches(
                withText(stableCopy.get(0).getResolveInfoAt(0).activityInfo.name)));
        onView(withText(stableCopy.get(1).getResolveInfoAt(0).activityInfo.name))
                .perform(click());
        waitForIdle();
        assertThat(chosen[0], is(toChoose));
    }

    @Test
    public void hasLastChosenActivityAndOtherProfile() throws Exception {
        Intent sendIntent = createSendImageIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(3);
        ResolveInfo toChoose = resolvedComponentInfos.get(1).getResolveInfoAt(0);

        when(ChooserWrapperActivity.sOverrides.resolverListController.getResolversForIntent(
                Mockito.anyBoolean(),
                Mockito.anyBoolean(),
                Mockito.isA(List.class))).thenReturn(resolvedComponentInfos);

        final ChooserWrapperActivity activity = mActivityRule
                .launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        // The other entry is filtered to the last used slot
        assertThat(activity.getAdapter().getCount(), is(2));

        ResolveInfo[] chosen = new ResolveInfo[1];
        ChooserWrapperActivity.sOverrides.onSafelyStartCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };

        // Make a stable copy of the components as the original list may be modified
        List<ResolvedComponentInfo> stableCopy =
                createResolvedComponentsForTestWithOtherProfile(3);
        // Check that the "Other Profile" activity is put in the right spot
        onView(withId(R.id.profile_button)).check(matches(
                withText(stableCopy.get(0).getResolveInfoAt(0).activityInfo.name)));
        onView(withText(stableCopy.get(1).getResolveInfoAt(0).activityInfo.name))
                .perform(click());
        waitForIdle();
        assertThat(chosen[0], is(toChoose));
    }

    public void pushedChooserTarget() {
        ResolveInfo[] chosen = new ResolveInfo[1];
        sOverrides.onSafelyStartCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };

        setChooserShortcuts(1);
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);
        when(sOverrides.resolverListController.getResolversForIntent(Mockito.anyBoolean(),
                Mockito.anyBoolean(),
                Mockito.isA(List.class))).thenReturn(resolvedComponentInfos);

        Intent sendIntent = createSendImageIntent();
        final ChooserWrapperActivity activity = mActivityRule
                .launchActivity(Intent.createChooser(sendIntent, null));

        waitForIdle();

        onView(withText("short chooser label 0"))
                .perform(click());
        waitForIdle();
        assertThat(chosen[0].resolvePackageName,
                is(ResolverDataProvider.createActivityInfo(0).packageName));
    }

    private Intent createSendImageIntent() {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, "testing intent sending");
        sendIntent.setType("image/jpeg");
        return sendIntent;
    }

    private List<ResolvedComponentInfo> createResolvedComponentsForTest(int numberOfResults) {
        List<ResolvedComponentInfo> infoList = new ArrayList<>(numberOfResults);
        for (int i = 0; i < numberOfResults; i++) {
            infoList.add(ResolverDataProvider.createResolvedComponentInfo(i));
        }
        return infoList;
    }

    private List<ResolvedComponentInfo> createResolvedComponentsForTestWithOtherProfile(
            int numberOfResults) {
        List<ResolvedComponentInfo> infoList = new ArrayList<>(numberOfResults);
        for (int i = 0; i < numberOfResults; i++) {
            if (i == 0) {
                infoList.add(ResolverDataProvider.createResolvedComponentInfoWithOtherId(i));
            } else {
                infoList.add(ResolverDataProvider.createResolvedComponentInfo(i));
            }
        }
        return infoList;
    }

    private void waitForIdle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private Integer getCount(
            UsageStatsManager usm, String packageName, String action, String annotation) {
        if (usm == null) {
            return 0;
        }
        Map<String, UsageStats> stats =
                usm.queryAndAggregateUsageStats(Long.MIN_VALUE, Long.MAX_VALUE);
        UsageStats packageStats = stats.get(packageName);
        if (packageStats == null || packageStats.mChooserCounts == null
                || packageStats.mChooserCounts.get(action) == null) {
            return 0;
        }
        return packageStats.mChooserCounts.get(action).getOrDefault(annotation, 0);
    }

    private void setChooserShortcuts(int numShortcuts) {
        ArrayList<ShortcutInfo> shortcuts = new ArrayList<>();
        for (int i = 0; i < numShortcuts; i++) {
           shortcuts.add(makeShortcut(i));
        }
        when(sOverrides.launcherApps.getShortcuts(
                Mockito.isA(LauncherApps.ShortcutQuery.class),
                Mockito.eq(UserHandle.SYSTEM)))
                .thenReturn(shortcuts);
    }

    private ShortcutInfo makeShortcut(int i) {
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_SEND, "image/jpeg");

            ComponentName component = new ComponentName("foo.bar", "foo.bar" + ".MainActivity");
            ShortcutInfo.Builder b = new ShortcutInfo.Builder(instrumentation.getContext(), "" + i)
                    .setActivity(component)
                    .setShortLabel("short chooser label " + i)
                    .setLongLabel("long chooser label" + i)
                    .setRank(i)
                    .setIntent(createSendImageIntent())
                    .setIcon(Icon.createWithResource(instrumentation.getContext(),
                            android.R.drawable.ic_menu_add))
                    .addChooserIntentFilter(
                            filter,
                            component);

            sOverrides.createPackageManager = pm -> {
                final PackageManager spied = spy(pm);
                try {
                    doAnswer(invocation -> ResolverDataProvider.createActivityInfo(i))
                            .when(spied).getActivityInfo(
                            Mockito.isA(ComponentName.class), Mockito.anyInt());
                } catch (Exception e) {
                    // this is ok, just not found
                    e.printStackTrace();
                }
                return spied;
            };
            return b.build();
        } catch (Exception e) {return null;}
    }
}