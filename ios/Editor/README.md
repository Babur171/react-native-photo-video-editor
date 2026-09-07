# Planned iOS editor

Milestone one contains no editing engine. A future full-screen editor will use `AVPlayer` for preview, AVFoundation composition/export APIs for timelines and final output, and Core Image/Core Graphics for photo filtering and composition. Metal will be introduced only when measured real-time rendering requirements justify it.

Temporary/output URL ownership and cleanup will be explicit. Photos framework integration will occur only when gallery saving is requested; the consuming app—not this library—must provide the relevant usage descriptions.
