package org.tasks.receivers;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.design.widget.Snackbar;
import android.view.View;
import android.view.WindowManager;

import com.todoroo.andlib.data.Property;
import com.todoroo.andlib.utility.DateUtilities;
import com.todoroo.astrid.api.AstridApiConstants;
import com.todoroo.astrid.data.Task;
import com.todoroo.astrid.service.TaskService;

import org.tasks.R;
import org.tasks.analytics.Tracker;

import javax.inject.Inject;

import timber.log.Timber;

public class RepeatConfirmationReceiver extends BroadcastReceiver {

    private final Property<?>[] REPEAT_RESCHEDULED_PROPERTIES =
            new Property<?>[]{
                    Task.ID,
                    Task.TITLE,
                    Task.DUE_DATE,
                    Task.HIDE_UNTIL,
                    Task.REPEAT_UNTIL
            };

    private final TaskService taskService;
    private final Activity activity;
    private Tracker tracker;

    @Inject
    public RepeatConfirmationReceiver(TaskService taskService, Activity activity, Tracker tracker) {
        this.taskService = taskService;
        this.activity = activity;
        this.tracker = tracker;
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        long taskId = intent.getLongExtra(AstridApiConstants.EXTRAS_TASK_ID, 0);

        if (taskId > 0) {
            long oldDueDate = intent.getLongExtra(AstridApiConstants.EXTRAS_OLD_DUE_DATE, 0);
            long newDueDate = intent.getLongExtra(AstridApiConstants.EXTRAS_NEW_DUE_DATE, 0);
            Task task = taskService.fetchById(taskId, REPEAT_RESCHEDULED_PROPERTIES);

            try {
                showSnackbar(activity.findViewById(R.id.task_list_coordinator), task, oldDueDate, newDueDate);
            } catch (WindowManager.BadTokenException e) { // Activity not running when tried to show dialog--rebroadcast
                Timber.e(e, e.getMessage());
                new Thread() {
                    @Override
                    public void run() {
                        context.sendBroadcast(intent);
                    }
                }.start();
            } catch (Exception e) {
                Timber.e(e, e.getMessage());
                tracker.reportException(e);
            }
        }
    }

    private void showSnackbar(View view, final Task task, final long oldDueDate, final long newDueDate) {
        String dueDateString = getRelativeDateAndTimeString(activity, newDueDate);
        String snackbarText = activity.getString(R.string.repeat_snackbar, task.getTitle(), dueDateString);

        Snackbar snackbar = Snackbar.make(view, snackbarText, Snackbar.LENGTH_LONG)
                .setActionTextColor(activity.getResources().getColor(R.color.snackbar_text_color))
                .setAction(R.string.DLG_undo, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        task.setDueDateAdjustingHideUntil(oldDueDate);
                        task.setCompletionDate(0L);
                        taskService.save(task);
                    }
                });
        snackbar.getView().setBackgroundColor(activity.getResources().getColor(R.color.snackbar_background));
        snackbar.show();
    }

    private String getRelativeDateAndTimeString(Context context, long date) {
        String dueString = date > 0 ? DateUtilities.getRelativeDay(context, date, false) : "";
        if (Task.hasDueTime(date)) {
            // TODO: localize this
            dueString = String.format("%s at %s", dueString, //$NON-NLS-1$
                    DateUtilities.getTimeString(context, date));
        }
        return dueString;
    }
}
