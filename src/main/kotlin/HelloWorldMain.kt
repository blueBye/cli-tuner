import io.quarkus.runtime.QuarkusApplication
import io.quarkus.runtime.annotations.QuarkusMain
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine
import kotlin.math.abs

@QuarkusMain
class HelloWorldMain : QuarkusApplication {
    override fun run(vararg args: String?): Int {
        println("Starting Quarkus Command-Line Guitar Tuner...")

        // Setup Audio Format: 44.1kHz, 16-bit, Mono, Signed, Little-Endian (Standard for Windows)
        val sampleRate = 44100f
        val format = AudioFormat(sampleRate, 16, 1, true, false)
        val info = DataLine.Info(TargetDataLine::class.java, format)

        if (!AudioSystem.isLineSupported(info)) {
            println("Error: Microphone is not supported or not found on this system.")
            return 1
        }

        val line = AudioSystem.getLine(info) as TargetDataLine
        line.open(format)
        line.start()

        val bufferSize = 4096
        val buffer = ByteArray(bufferSize)
        val floatBuffer = FloatArray(bufferSize / 2)

        println("Listening to microphone... Play a string! (Press Ctrl+C to stop)")

        while (true) {
            val bytesRead = line.read(buffer, 0, buffer.size)
            if (bytesRead > 0) {
                // Convert 16-bit PCM bytes to normalized floats (-1.0 to 1.0)
                for (i in 0 until bytesRead / 2) {
                    val sample = (buffer[2 * i].toInt() and 0xFF) or (buffer[2 * i + 1].toInt() shl 8)
                    floatBuffer[i] = sample.toFloat() / 32768.0f
                }

                val frequency = estimatePitch(floatBuffer, sampleRate)

                // Filter out noise and irrelevant frequencies
                if (frequency > 50f && frequency < 400f) {
                    val tuningStatus = getClosestGuitarString(frequency)
                    // Use \r to overwrite the same line in the Windows console
                    print("\rDetected Pitch: %6.2f Hz | %s".format(frequency, tuningStatus))
                }
            }
        }
    }

    private fun estimatePitch(buffer: FloatArray, sampleRate: Float): Float {
        var maxCorrelation = 0f
        var bestPeriod = -1

        // Autocorrelation parameters to detect frequencies roughly between 60 Hz and 1000 Hz
        val minPeriod = (sampleRate / 1000f).toInt()
        val maxPeriod = (sampleRate / 60f).toInt()

        for (period in minPeriod until maxPeriod) {
            var correlation = 0f
            for (i in 0 until buffer.size - period) {
                correlation += buffer[i] * buffer[i + period]
            }
            if (correlation > maxCorrelation) {
                maxCorrelation = correlation
                bestPeriod = period
            }
        }

        return if (bestPeriod != -1) sampleRate / bestPeriod else 0f
    }

    private fun getClosestGuitarString(frequency: Float): String {
        val strings = mapOf(
            "E2 (Low)" to 82.41f,
            "A2"       to 110.00f,
            "D3"       to 146.83f,
            "G3"       to 196.00f,
            "B3"       to 246.94f,
            "E4 (High)" to 329.63f
        )

        var closestName = "Unknown"
        var minDiff = Float.MAX_VALUE
        var targetFrequency = 0f

        for ((name, targetFreq) in strings) {
            val diff = abs(frequency - targetFreq)
            if (diff < minDiff) {
                minDiff = diff
                closestName = name
                targetFrequency = targetFreq
            }
        }

        val diff = frequency - targetFrequency
        val padding = " ".repeat(20) // to clear trailing console characters

        val advice = when {
            diff < -1.5f -> "Tune UP   (->)"
            diff > 1.5f  -> "Tune DOWN (<-)"
            else         -> "IN TUNE   (==)"
        }

        return "Closest: %-9s | Target: %6.2f Hz | %s%s".format(closestName, targetFrequency, advice, padding)
    }
}
