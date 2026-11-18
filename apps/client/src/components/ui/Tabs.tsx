import { forwardRef, type ComponentPropsWithoutRef, type ElementRef } from 'react';
import * as Primitive from '@radix-ui/react-tabs';
import { cn } from './cn';

const List = forwardRef<
  ElementRef<typeof Primitive.List>,
  ComponentPropsWithoutRef<typeof Primitive.List>
>(function List({ className, ...rest }, ref) {
  return <Primitive.List ref={ref} className={cn('flex gap-1 border-b border-border', className)} {...rest} />;
});

const Trigger = forwardRef<
  ElementRef<typeof Primitive.Trigger>,
  ComponentPropsWithoutRef<typeof Primitive.Trigger>
>(function Trigger({ className, ...rest }, ref) {
  return (
    <Primitive.Trigger
      ref={ref}
      className={cn(
        '-mb-px inline-flex min-h-11 items-center border-b-2 border-transparent px-3 text-sm font-medium text-muted transition-colors duration-fast hover:text-text focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary data-[state=active]:border-primary data-[state=active]:text-primary',
        className,
      )}
      {...rest}
    />
  );
});

const Content = forwardRef<
  ElementRef<typeof Primitive.Content>,
  ComponentPropsWithoutRef<typeof Primitive.Content>
>(function Content({ className, ...rest }, ref) {
  return (
    <Primitive.Content
      ref={ref}
      className={cn(
        'pt-4 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary',
        className,
      )}
      {...rest}
    />
  );
});

export const Tabs = { Root: Primitive.Root, List, Trigger, Content };
