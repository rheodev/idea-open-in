#import <Cocoa/Cocoa.h>

int main(int argc, char **argv) {
    @autoreleasepool {
        if (argc != 3) {
            fprintf(stderr, "usage: open-in-service-launcher <service-name> <directory>\n");
            return 64;
        }
        NSString *service = [NSString stringWithUTF8String:argv[1]];
        NSString *directory = [NSString stringWithUTF8String:argv[2]];
        BOOL isDirectory = NO;
        if (![[NSFileManager defaultManager] fileExistsAtPath:directory isDirectory:&isDirectory] || !isDirectory) {
            fprintf(stderr, "Not a directory: %s\n", argv[2]);
            return 66;
        }
        NSPasteboard *board = [NSPasteboard pasteboardWithUniqueName];
        [board declareTypes:@[NSPasteboardTypeFileURL] owner:nil];
        [board writeObjects:@[[NSURL fileURLWithPath:directory]]];
        // Invokes each terminal's own macOS Service ("New ... Window Here"). Unlike
        // AppleScript automation this does not require Automation permission and
        // therefore never produces the repeated macOS confirmation prompt.
        if (!NSPerformService(service, board)) {
            fprintf(stderr, "macOS Service failed: %s\n", argv[1]);
            return 69;
        }
        return 0;
    }
}
