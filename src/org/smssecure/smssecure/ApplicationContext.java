/*
 * Copyright (C) 2013 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.smssecure.smssecure;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import org.smssecure.smssecure.crypto.PRNGFixes;
import org.smssecure.smssecure.dependencies.AxolotlStorageModule;
import org.smssecure.smssecure.dependencies.InjectableType;
import org.smssecure.smssecure.jobs.persistence.EncryptingJobSerializer;
import org.smssecure.smssecure.jobs.requirements.MasterSecretRequirementProvider;
import org.smssecure.smssecure.jobs.requirements.ServiceRequirementProvider;
import org.smssecure.smssecure.mms.MmsMediaConstraints;
import org.smssecure.smssecure.util.SMSSecurePreferences;
import org.whispersystems.jobqueue.JobManager;
import org.whispersystems.jobqueue.dependencies.DependencyInjector;
import org.whispersystems.jobqueue.requirements.NetworkRequirementProvider;
import org.whispersystems.libaxolotl.logging.AxolotlLoggerProvider;
import org.whispersystems.libaxolotl.util.AndroidAxolotlLogger;

import java.security.Security;

import dagger.ObjectGraph;

/**
 * Will be called once when the SMSSecure process is created.
 *
 * We're using this as an insertion point to patch up the Android PRNG disaster
 * and to initialize the job manager.
 *
 * @author Moxie Marlinspike
 */
public class ApplicationContext extends Application implements DependencyInjector {

  private JobManager jobManager;
  private ObjectGraph objectGraph;

  public static ApplicationContext getInstance(Context context) {
    return (ApplicationContext)context.getApplicationContext();
  }

  @Override
  public void onCreate() {
    initializeRandomNumberFix();
    initializeLogging();
    initializeDependencyInjection();
    initializeJobManager();
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

    int kiloBytes = prefs.getInt("pref_mms_size", 220);
    if(kiloBytes > 0)
      MmsMediaConstraints.MAX_MESSAGE_SIZE = 1024 * kiloBytes;
  }

  @Override
  public void injectDependencies(Object object) {
    if (object instanceof InjectableType) {
      objectGraph.inject(object);
    }
  }

  public JobManager getJobManager() {
    return jobManager;
  }

  private void initializeRandomNumberFix() {
    PRNGFixes.apply();
  }

  private void initializeLogging() {
    AxolotlLoggerProvider.setProvider(new AndroidAxolotlLogger());
  }

  private void initializeJobManager() {
    this.jobManager = JobManager.newBuilder(this)
                                .withName("SMSSecureJobs")
                                .withDependencyInjector(this)
                                .withJobSerializer(new EncryptingJobSerializer())
                                .withRequirementProviders(new MasterSecretRequirementProvider(this),
                                                          new ServiceRequirementProvider(this),
                                                          new NetworkRequirementProvider(this))
                                .withConsumerThreads(5)
                                .build();
  }

  private void initializeDependencyInjection() {
    this.objectGraph = ObjectGraph.create(new AxolotlStorageModule(this));
  }

}
