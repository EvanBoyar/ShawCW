# Shaw CW

Shaw CW is an accessibility app for radio operators who are deaf or hard of hearing. It listens through your phone's microphone for Morse code (CW) tones coming from a radio's speaker and turns each tone into something you can feel or see: a vibration, a flash of the camera light, and an on screen color that tracks the pitch.

## Install

The easiest way is to download a ready built APK:

- Go to the [latest release](https://github.com/EvanBoyar/ShawCW/releases/latest).
- Download the APK (named `shaw-cw-<version>.apk`) attached to the latest release.
- Open it on your phone. You may need to allow installing apps from this source the first time.

Each pushed release builds and attaches a fresh APK automatically, so the Releases page always has the current version.

## What it does

When the app hears a tone in your chosen frequency band, it fires whichever outputs you have turned on:

- **Haptic.** The phone vibrates for as long as the tone lasts. This is the main way the app is meant to be used.
- **Flashlight.** The camera light flashes with the tone.
- **Color.** A circle on screen lights up, and its color follows the pitch of the tone, so you can see when an operator is off the zero beat frequency.

You can use any combination of these at once, and the app keeps working with the screen off.

## Using it

- Tap **Start listening** to begin. Tap it again to stop.
- The chips below the circle turn each output on and off quickly.
- The number near the top shows the detected pitch in hertz while a tone is present.
- The bar graph is a live view of the sound in your band. Tap the bar chart button in the top corner to hide it if it is distracting.
- It's most accurate below speeds of about 30 WPM. However, I expect to be able to raise this significantly in coming versions.

## Settings

Open settings with the gear icon.

- **Tone frequencies.** Center is the tone you hear when the radio is zero beat. Low and high bound how far an operator may vary. The defaults are 500, 600 and 700 Hz.
- **Feedback.** Turn haptic, flashlight and color on or off.
- **Color palette.** Choose how pitch maps to color.
- **Vibration calibration.** The phone's own buzz leaks into the microphone and can trip the detector. Stop listening, put the phone on a quiet surface, and tap **Calibrate**. The app measures the vibration and filters those frequencies out.

## Tips

- Hold the phone near the radio's speaker, or set the audio so the CW tone is clear.
- If a quiet band still feels too sensitive or not sensitive enough, adjust the low and high frequencies to bracket the tone more tightly.
- Detection ignores steady background hiss; it reacts to a clear, single pitch.

## Privacy

Everything runs on the phone. The app records audio only while you are listening, processes it on the spot to find tones, and keeps nothing. It needs the microphone to listen, the camera flash for the light output, and notifications so it can keep running with the screen off.

## Building from source

The project is a standard Gradle Android app.

```
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

## Support

If Shaw CW is useful to you, you can support its development at [buymeacoffee.com/elbow](https://buymeacoffee.com/elbow).
You can also lend the *other* kind of support by logging an issue or submitting a PR.

## How this got made

For many years I've been interested in assistive devices for people with desires that conflict with their bodies. When I was a kid and learned the partially-true story about Beethoven dealing with his deafness by "listening" to music through the tactile sensation of vibrations, I wondered if a device could be built that transduced vibrations in the air into lower-frequency vibrations that could be felt on the skin. With the advent of the smartphone in '07, I realized that the vibration motor could be used to at least help find the beat of a song or its main tones. Even back then I realized there's nothing special about Morse that sets it apart from music, so it could even more easily work for that. My idea would have to wait until coding agents made it easy enough to whip something like this up in an evening for me to get the spare time to make it!

This app is named after the inventor [William E. Shaw](https://www.britishdeafnews.co.uk/the-life-of-william-shaw/), a deaf inventor who created scores of devices for the hard of hearing. At the time of writing he doesn't appear to have a Wikipedia article so if you're reading this, get on it!

## License

Copyright 2026 Evan Boyar

THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
