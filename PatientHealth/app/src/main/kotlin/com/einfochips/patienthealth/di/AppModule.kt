package com.einfochips.patienthealth.di

import android.content.Context
import androidx.room.Room
import com.einfochips.patienthealth.BuildConfig
import com.einfochips.patienthealth.data.AppDatabase
import com.einfochips.patienthealth.data.AuditDao
import com.einfochips.patienthealth.data.DatabaseKeyProvider
import com.einfochips.patienthealth.data.FhirApi
import com.einfochips.patienthealth.data.FhirClient
import com.einfochips.patienthealth.data.PatientDao
import com.einfochips.patienthealth.data.PatientRepository
import com.einfochips.patienthealth.data.VitalDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase {
        System.loadLibrary("sqlcipher")
        val factory = SupportOpenHelperFactory(DatabaseKeyProvider(context).passphrase())
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .openHelperFactory(factory)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()
    }

    @Provides fun patientDao(db: AppDatabase): PatientDao = db.patientDao()
    @Provides fun vitalDao(db: AppDatabase): VitalDao = db.vitalDao()
    @Provides fun auditDao(db: AppDatabase): AuditDao = db.auditDao()

    @Provides @Singleton
    fun okHttp(): OkHttpClient {
        val tls = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS).tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2).build()
        // Dev flavor talks to a local server over cleartext (see src/dev network security config).
        val specs = if (BuildConfig.DEBUG) listOf(tls, ConnectionSpec.CLEARTEXT) else listOf(tls)
        return OkHttpClient.Builder()
            .connectionSpecs(specs)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides @Singleton
    fun fhirApi(http: OkHttpClient): FhirApi = FhirClient(BuildConfig.FHIR_BASE_URL, http)

    @Provides @Singleton
    fun repository(p: PatientDao, v: VitalDao, a: AuditDao, api: FhirApi) = PatientRepository(p, v, a, api)
}
